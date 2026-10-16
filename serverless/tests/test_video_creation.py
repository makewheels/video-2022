"""Compare ordinary creation writes with complete old MVC/Mongo snapshots."""

import copy
import json
from dataclasses import replace
from datetime import UTC, datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path

import pytest
from starlette.testclient import TestClient
from test_user_mongo_reads import golden_document
from test_user_mongo_reads import isolated_database as isolated_database
from test_watch_info import Urls

from video_serverless.adapters.mongo_video_creation import MongoCreationWrites
from video_serverless.adapters.user_asgi import create_user_asgi
from video_serverless.backend_bootstrap import build_backend_application
from video_serverless.business.video_creation import CreationArgumentError, VideoCreation
from video_serverless.ports.video_creation import CreateVideo

CASES = json.loads((Path(__file__).parents[1] / "contracts/upload-create/cases.json").read_text(encoding="utf-8"))
NOW = datetime(2024, 1, 31, 23, 0, tzinfo=UTC)
LOCAL = lambda: NOW.astimezone(timezone(timedelta(hours=8)))  # noqa: E731


class FixedIds:
    def long_id(self, prefix=""):
        return {"v_": "fixture-video", "f_": "fixture-file"}[prefix]

    def short_id(self):
        return "fixture-watch"


def normalize(document):
    if document is None:
        return None
    document = copy.deepcopy(document)
    for field in ("createTime", "updateTime"):
        assert isinstance(document[field], datetime)
        document[field] = "<server-date>"
    if "key" in document:
        assert "/202402/" in document["key"]
        document["key"] = document["key"].replace("/202402/", "/<local-month>/")
    return document


@pytest.mark.parametrize("case", [row for row in CASES if row["response"]["code"] in {0, 21}])
def test_typed_creation_matches_complete_java_bson(isolated_database, case):
    database = isolated_database
    database.video.delete_many({})
    database.file.delete_many({})
    dto = json.loads(case["input"])
    size = dto.get("size")
    if size in ("", "null"):
        size = None
    request = CreateVideo(None if dto.get("videoType") is None else str(dto["videoType"]).lower()
                          if isinstance(dto["videoType"], bool) else str(dto["videoType"]),
                          str(dto["rawFilename"]).lower() if isinstance(dto.get("rawFilename"), bool)
                          else dto.get("rawFilename"), None if size is None else int(Decimal(str(size))),
                          dto.get("transcodeMode"))
    creator = VideoCreation(MongoCreationWrites(lambda: database), FixedIds(), lambda: NOW, LOCAL,
                            "http://localhost:5022")
    if case["response"]["code"] == 21:
        with pytest.raises(CreationArgumentError):
            creator.create("fixture-uploader", request)
    else:
        result = creator.create("fixture-uploader", request)
        assert result.video_id == "fixture-video" and result.file_id == "fixture-file"
        assert result.watch_id == "fixture-watch"
        assert result.watch_url == case["response"]["data"]["watchUrl"]
    assert normalize(database.video.find_one()) == case["video"]
    assert normalize(database.file.find_one()) == case["file"]


@pytest.mark.parametrize("failure,expected", [(1, []), (2, ["file"]), (3, ["file", "video"])])
def test_partial_write_failure_preserves_legacy_order_without_retries(failure, expected):
    calls, saved = [], []

    class Writes:
        def save(self, kind, document):
            calls.append(kind)
            if len(calls) == failure:
                raise RuntimeError("synthetic persistence failure")
            saved.append((kind, copy.deepcopy(document)))

        def save_file(self, document):
            self.save("file", document)

        def save_video(self, document):
            self.save("video", document)

    creator = VideoCreation(Writes(), FixedIds(), lambda: NOW, LOCAL, "http://localhost:5022")
    with pytest.raises(RuntimeError, match="synthetic persistence failure"):
        creator.create("fixture-uploader", CreateVideo("USER_UPLOAD", "a.MP4", 42))
    assert len(calls) == failure
    assert [kind for kind, _ in saved] == expected
    if saved:
        assert "videoId" not in saved[0][1] and "key" not in saved[0][1]


def test_youtube_is_separate_and_does_not_leave_partial_ordinary_writes():
    class NeverWrite:
        def save_file(self, document):
            pytest.fail("YouTube must not enter ordinary creation")

    creator = VideoCreation(NeverWrite(), FixedIds(), lambda: NOW, LOCAL, "http://localhost:5022")
    with pytest.raises(NotImplementedError):
        creator.create("fixture-uploader", CreateVideo("YOUTUBE"))


@pytest.mark.parametrize("case", CASES)
def test_creation_http_binding_response_and_complete_mongo_match_java(isolated_database, case):
    database = isolated_database
    for name in ("user", "video", "file", "requestLog", "exceptionLog"):
        database[name].delete_many({})
    user = golden_document() | {"_id": "fixture-uploader", "token": "fixture-upload-token"}
    database.user.insert_one(user)
    backend = build_backend_application(lambda: database, Urls(), profile="prod", local_clock=LOCAL,
                                         display_timezone=UTC, worker=1, datacenter=1,
                                         creation_base_url="http://localhost:5022")
    application = replace(backend.upload, creation=VideoCreation(MongoCreationWrites(lambda: database),
                          FixedIds(), lambda: NOW, LOCAL, "http://localhost:5022"))
    with TestClient(create_user_asgi(application)) as client:
        response = client.post("/video/create", content=case["input"].encode(),
                               headers={"token": user["token"], "Content-Type": "application/json"})
    assert response.status_code == 200 and response.headers["Content-Type"] == "application/json"
    assert response.json() == case["response"]
    assert normalize(database.video.find_one()) == case["video"]
    assert normalize(database.file.find_one()) == case["file"]
    assert database.requestLog.count_documents({}) == 1
    assert database.exceptionLog.count_documents({}) == int(case["response"]["code"] != 0)


def test_creation_is_opt_in_auth_precedes_binding_and_unknown_routes_do_not_write(isolated_database):
    database = isolated_database
    for name in ("user", "video", "file", "requestLog", "exceptionLog", "id_counters"):
        database[name].delete_many({})
    options = dict(profile="prod", local_clock=LOCAL, display_timezone=UTC, worker=1, datacenter=1)
    disabled = build_backend_application(lambda: database, Urls(), **options)
    with TestClient(create_user_asgi(disabled)) as client:
        assert client.post("/video/create", content="invalid").status_code == 404
    enabled = build_backend_application(lambda: database, Urls(), **options, creation_base_url="http://localhost:5022")
    assert enabled.upload.cycle is enabled.users.cycle
    with TestClient(create_user_asgi(enabled)) as client:
        for method in ("POST", "OPTIONS", "GET"):
            assert client.request(method, "/video/create", content="invalid").status_code == 401
    for name in ("video", "file", "requestLog", "exceptionLog", "id_counters"):
        assert database[name].count_documents({}) == 0


def test_composed_creation_allocates_real_shared_ids_and_links_file_and_video(isolated_database):
    database = isolated_database
    for name in ("user", "video", "file", "requestLog", "exceptionLog", "id_counters"):
        database[name].delete_many({})
    user = golden_document()
    database.user.insert_one(user)
    backend = build_backend_application(lambda: database, Urls(), profile="prod", local_clock=LOCAL,
                                         display_timezone=UTC, worker=1, datacenter=1,
                                         creation_base_url="https://example.invalid")
    with TestClient(create_user_asgi(backend)) as client:
        for _ in range(2):
            response = client.post("/video/create", headers={"token": user["token"]},
                                   json={"videoType": "USER_UPLOAD", "rawFilename": "a.MP4", "size": 42})
            assert response.json()["code"] == 0
            result = response.json()["data"]
            assert result["videoId"].startswith("v_") and result["fileId"].startswith("f_")
            video = database.video.find_one({"_id": result["videoId"]})
            assert video["watch"]["watchId"] == result["watchId"]
            assert video["uploaderId"] == user["_id"]
    assert database.video.count_documents({}) == database.file.count_documents({}) == 2
    assert database.requestLog.count_documents({}) == 2
    assert database.exceptionLog.count_documents({}) == 0
