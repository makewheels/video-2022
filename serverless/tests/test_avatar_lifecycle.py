from datetime import datetime

import pytest
from starlette.testclient import TestClient
from test_user_mongo_reads import golden_document
from test_user_mongo_reads import isolated_database as isolated_database

from video_serverless.adapters.user_asgi import create_user_asgi
from video_serverless.ports.avatars import UploadCredentials
from video_serverless.user_bootstrap import build_user_application


class Objects:
    def __init__(self):
        self.calls = []
        self.present = True
        self.authorized = True

    def upload_credentials(self, key):
        self.calls.append(("credentials", key))
        return (UploadCredentials("fixture-bucket", key, "fixture.invalid", "fixture-access", "fixture-secret",
                                  "fixture-session", "2040-01-01T00:00:00Z") if self.authorized else None)

    def exists(self, key):
        self.calls.append(("exists", key))
        return self.present

    def sign(self, key, validity_seconds):
        self.calls.append(("sign", key, validity_seconds))
        return "https://example.invalid/fixture-avatar"


@pytest.fixture
def composed(isolated_database):
    database = isolated_database
    for name in ("user", "file", "id_counters", "requestLog", "exceptionLog"):
        database[name].delete_many({})
    database.user.insert_one(golden_document())
    objects = Objects()
    app = build_user_application(lambda: database, objects, profile="prod", local_clock=datetime.now,
                                 worker=1, datacenter=1, avatar_objects=objects)
    with TestClient(create_user_asgi(app)) as client:
        yield database, objects, client


def get(client, path, params=None, *, authenticated=True):
    return client.get("/user/" + path, params=params,
                      headers={"token": "fixture-legacy-token"} if authenticated else {}).json()


def test_create_credentials_finish_and_dynamic_read_preserve_legacy_file_and_user(composed):
    database, objects, client = composed
    original = database.user.find_one()
    created = get(client, "createAvatarFile")["data"]
    file_id, key = created["fileId"], created["key"]
    assert file_id.startswith("f_") and key == "avatar/fixture-user-reads/" + file_id
    document = database.file.find_one()
    assert document["fileType"] == "AVATAR" and document["uploaderId"] == "fixture-user-reads"
    assert document["fileStatus"] == "CREATED" and document["provider"] == "ALIYUN_OSS"
    assert document["deleted"] is False and document["hasLink"] is False
    assert database.user.find_one() == original
    credentials = get(client, "getAvatarUploadCredentials", {"fileId": file_id})
    assert credentials["data"] == {"bucket": "fixture-bucket", "key": key, "endpoint": "fixture.invalid",
                                   "accessKeyId": "fixture-access", "secretKey": "fixture-secret",
                                   "sessionToken": "fixture-session", "expiration": "2040-01-01T00:00:00Z",
                                   "fileId": file_id}
    assert get(client, "avatarUploadFinish", {"fileId": file_id})["code"] == 0
    stored = database.user.find_one()
    assert stored["avatarFileId"] == file_id and stored["token"] == original["token"]
    assert stored["avatarUrl"] == original["avatarUrl"]
    assert database.file.find_one() == document
    profile = get(client, "getMyProfile")
    assert profile["data"]["avatarUrl"] == "https://example.invalid/fixture-avatar"
    assert objects.calls == [("credentials", key), ("exists", key), ("sign", key, 86400)]


@pytest.mark.parametrize("operation", ["getAvatarUploadCredentials", "avatarUploadFinish"])
@pytest.mark.parametrize("kind,error", [("missing", 31), ("wrong-type", 31), ("other-owner", 33)])
def test_file_checks_precede_cloud_calls_and_do_not_change_user(composed, operation, kind, error):
    database, objects, client = composed
    if kind != "missing":
        database.file.insert_one({"_id": "fixture-file", "key": "fixture/key",
                                  "fileType": "RAW_VIDEO" if kind == "wrong-type" else "AVATAR",
                                  "uploaderId": "other-user" if kind == "other-owner" else "fixture-user-reads"})
    before = database.user.find_one()
    assert get(client, operation, {"fileId": "fixture-file"})["code"] == error
    assert objects.calls == []
    assert database.user.find_one() == before
    assert database.exceptionLog.find_one()["systemErrorCode"] == error


def test_absent_object_and_failed_credentials_keep_old_errors_without_binding(composed):
    database, objects, client = composed
    file_id = get(client, "createAvatarFile")["data"]["fileId"]
    before = database.user.find_one()
    objects.present = False
    assert get(client, "avatarUploadFinish", {"fileId": file_id})["code"] == 31
    objects.authorized = False
    assert get(client, "getAvatarUploadCredentials", {"fileId": file_id})["code"] == 34
    assert database.user.find_one() == before


def test_anonymous_legacy_behavior_is_reproduced_only_in_isolated_application(composed):
    database, objects, client = composed
    created = get(client, "createAvatarFile", authenticated=False)["data"]
    assert created["key"] == "avatar/null/" + created["fileId"]
    assert "uploaderId" not in database.file.find_one()
    assert get(client, "getAvatarUploadCredentials", {"fileId": "missing"}, authenticated=False)["code"] == 31
    assert get(client, "getAvatarUploadCredentials", {"fileId": created["fileId"]},
               authenticated=False)["code"] == 1
    assert objects.calls == []
