"""Real isolated HTTP/Mongo comparison with old file upload fixtures; cloud calls stay synthetic."""

import copy
import json
from datetime import UTC, datetime
from pathlib import Path
from types import SimpleNamespace

import pytest
from bson import Int64, ObjectId
from starlette.testclient import TestClient
from test_user_mongo_reads import golden_document
from test_user_mongo_reads import isolated_database as isolated_database
from test_watch_info import Urls

from video_serverless.adapters.mongo_file_uploads import MongoUploadFiles
from video_serverless.adapters.oss_file_uploads import OssUploadObjects
from video_serverless.adapters.user_asgi import create_user_asgi
from video_serverless.backend_bootstrap import build_backend_application
from video_serverless.business.file_uploads import FileUploads, UploadFileError
from video_serverless.ports.avatars import UploadCredentials
from video_serverless.ports.file_uploads import UploadedObject

CASES = json.loads((Path(__file__).parents[1] / "contracts/upload-files/cases.json").read_text(encoding="utf-8"))
NOW = datetime(2024, 2, 1, tzinfo=UTC)
CREDENTIALS = UploadCredentials("fixture-bucket", "fixture-key", "https://example.invalid", "fixture-not-real",
                                "fixture-not-real", "fixture-not-real", "2030-01-01T00:00:00Z")


def inflate(document):
    result = copy.deepcopy(document)
    for name in ("createTime", "updateTime"):
        if result.get(name) == "<server-date>":
            result[name] = NOW
    result["key"] = result["key"].replace("/<local-month>/", "/202402/")
    return result


def normalize(document):
    result = copy.deepcopy(document)
    for name in ("createTime", "updateTime"):
        if result.get(name) is not None:
            assert isinstance(result[name], datetime)
            result[name] = "<server-date>"
    result["key"] = result["key"].replace("/202402/", "/<local-month>/")
    if "uploadTime" in result:
        result["uploadTime"] = int(result["uploadTime"].replace(tzinfo=UTC).timestamp() * 1000)
    return result


class Objects:
    def __init__(self, scenario="success"):
        self.scenario = scenario
        self.calls = []

    def _call(self, operation, key):
        self.calls.append((operation, key))
        if self.scenario == "remote-failure":
            raise RuntimeError("synthetic OSS failure")

    def upload_credentials(self, key):
        self._call("credentials", key)
        return None if self.scenario == "remote-null" else CREDENTIALS

    def metadata(self, key):
        self._call("metadata", key)
        return None if self.scenario == "remote-null" else UploadedObject(123, "fixture-etag",
                                                                         datetime.fromtimestamp(1, UTC))


def backend(database, objects=None, **options):
    return build_backend_application(lambda: database, Urls(), profile="prod", local_clock=lambda: NOW,
                                     display_timezone=UTC, worker=1, datacenter=1, upload_objects=objects, **options)


@pytest.fixture
def database(isolated_database):
    for name in ("user", "video", "file", "requestLog", "exceptionLog", "id_counters"):
        isolated_database[name].delete_many({})
    isolated_database.user.insert_one(golden_document() | {"_id": "fixture-uploader", "token": "fixture-upload-token"})
    return isolated_database


@pytest.mark.parametrize("case", CASES)
def test_http_credentials_and_completion_match_java_documents_and_side_effects(database, case):
    database.file.insert_one(inflate(case["before"]))
    objects = Objects(case["scenario"])
    application = backend(database, objects)
    assert application.file_uploads.cycle is application.users.cycle
    params = {} if case["scenario"] == "missing-parameter" else {
        "fileId": "absent" if case["scenario"] == "missing-file" else "fixture-file"}
    with TestClient(create_user_asgi(application)) as client:
        response = client.get("/file/" + case["operation"], params=params, headers={"token": "fixture-upload-token"})
    assert response.status_code == 200 and response.headers["Content-Type"] == "application/json"
    assert response.json() == case["response"]
    saved = database.file.find_one()
    assert normalize(saved) == case["after"]
    if "size" in saved:
        assert isinstance(saved["size"], Int64)
    assert database.requestLog.count_documents({}) == case["requestLogs"]
    assert database.exceptionLog.count_documents({}) == case["exceptionLogs"]
    assert len(objects.calls) == case["remoteCalls"]


def test_files_routes_are_opt_in_and_authentication_precedes_parameters(database):
    objects = Objects()
    with TestClient(create_user_asgi(backend(database))) as client:
        assert client.get("/file/getUploadCredentials").status_code == 404
    with TestClient(create_user_asgi(backend(database, objects))) as client:
        for path in ("/file/getUploadCredentials", "/file/uploadFinish"):
            for method in ("GET", "HEAD", "OPTIONS", "POST"):
                assert client.request(method, path).status_code == 401
    assert not objects.calls
    assert database.file.count_documents({}) == 0
    assert database.requestLog.count_documents({}) == database.exceptionLog.count_documents({}) == 0


def test_creation_credentials_and_repeated_completion_keep_remote_metadata_and_no_tasks(database):
    objects = Objects()
    application = backend(database, objects, creation_base_url="https://example.invalid")
    with TestClient(create_user_asgi(application)) as client:
        headers = {"token": "fixture-upload-token"}
        result = client.post("/video/create", headers=headers,
                             json={"videoType": "USER_UPLOAD", "rawFilename": "a.MP4", "size": 9999}).json()
        assert result["code"] == 0
        file_id = result["data"]["fileId"]
        before = database.file.find_one()
        assert "size" not in before
        assert client.get("/file/getUploadCredentials", params={"fileId": file_id}, headers=headers).json()["code"] == 0
        for _ in range(2):
            assert client.get("/file/uploadFinish", params={"fileId": file_id}, headers=headers).json()["code"] == 0
        saved = database.file.find_one()
        assert saved["size"] == 123 and saved["fileStatus"] == "READY"
        assert saved["createTime"] == before["createTime"] and saved["updateTime"] == before["updateTime"]
        objects.scenario = "remote-failure"
        assert client.get("/file/uploadFinish", params={"fileId": file_id}, headers=headers).json()["code"] == 1
        assert database.file.find_one() == saved
        assert client.get("/video/rawFileUploadFinish", params={"videoId": result["data"]["videoId"]},
                          headers=headers).status_code == 404
    assert [name for name, _ in objects.calls] == ["credentials", "metadata", "metadata", "metadata"]
    assert database.video.find_one()["status"] == "CREATED"
    assert database.requestLog.count_documents({}) == 5 and database.exceptionLog.count_documents({}) == 1


def test_existing_object_id_file_and_null_optional_remote_metadata(database):
    document = inflate(CASES[0]["before"])
    document["_id"] = ObjectId("507f1f77bcf86cd799439011")
    database.file.insert_one(document)

    class EmptyMetadata(Objects):
        def metadata(self, key):
            return UploadedObject(0, None, None)

    FileUploads(MongoUploadFiles(lambda: database), EmptyMetadata()).finish("fixture-uploader", str(document["_id"]))
    saved = database.file.find_one()
    assert isinstance(saved["_id"], ObjectId) and isinstance(saved["size"], Int64)
    assert saved["size"] == 0 and saved["fileStatus"] == "READY"
    assert "uploadTime" not in saved and "etag" not in saved


def test_write_failure_is_not_retried_or_hidden(database):
    database.file.insert_one(inflate(CASES[0]["before"]))
    objects = Objects()

    class Failing(MongoUploadFiles):
        calls = 0

        def save(self, document):
            self.calls += 1
            raise RuntimeError("synthetic Mongo failure")

    files = Failing(lambda: database)
    before = database.file.find_one()
    with pytest.raises(RuntimeError, match="synthetic Mongo failure"):
        FileUploads(files, objects).finish("fixture-uploader", "fixture-file")
    assert files.calls == len(objects.calls) == 1
    assert database.file.find_one() == before


def test_legacy_missing_request_holder_is_checked_after_file_existence(database):
    objects = Objects()
    uploads = FileUploads(MongoUploadFiles(lambda: database), objects)
    with pytest.raises(UploadFileError) as missing:
        uploads.credentials(None, "absent")
    assert missing.value.code == 31
    database.file.insert_one(inflate(CASES[0]["before"]))
    with pytest.raises(UploadFileError) as no_holder:
        uploads.credentials(None, "fixture-file")
    assert no_holder.value.code == 15
    assert not objects.calls


@pytest.mark.parametrize("bad_metadata", [False, True])
def test_sdk_adapter_reads_headers_and_closes_stream_even_when_mapping_fails(bad_metadata):
    from oss2.models import GetObjectResult

    closed, calls = [], []
    response = SimpleNamespace(status=200, request_id="fixture", headers={
        "content-length": "123", "etag": '"fixture-etag"', "last-modified": "Thu, 01 Jan 1970 00:00:01 GMT"},
        response=SimpleNamespace(close=lambda: closed.append(True)))
    result = GetObjectResult(response)
    if bad_metadata:
        result.last_modified = "invalid"

    def get_object(key):
        calls.append(key)
        return result

    objects = OssUploadObjects(Objects(), SimpleNamespace(get_object=get_object))
    if bad_metadata:
        with pytest.raises(TypeError):
            objects.metadata("fixture-key")
    else:
        assert objects.metadata("fixture-key") == UploadedObject(123, "fixture-etag", datetime.fromtimestamp(1, UTC))
    assert calls == ["fixture-key"] and closed == [True]
