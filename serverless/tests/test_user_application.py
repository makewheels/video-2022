from datetime import datetime

import pytest
from bson import ObjectId
from starlette.testclient import TestClient
from test_user_mongo_reads import golden_document
from test_user_mongo_reads import isolated_database as isolated_database
from test_user_reads import ROOT, comparison

from video_serverless.adapters.user_asgi import create_user_asgi
from video_serverless.user_bootstrap import build_user_application

PHONE = "00000000000"


class NoMedia:
    def sign(self, key, validity_seconds):
        pytest.fail("These login cases must not access OSS")


@pytest.fixture
def composed(isolated_database):
    database = isolated_database
    for name in ("user", "wallet", "verification_codes", "id_counters", "client", "session", "requestLog",
                 "exceptionLog", "file"):
        database[name].delete_many({})
    application = build_user_application(lambda: database, NoMedia(), profile="prod",
                                         local_clock=lambda: datetime(2026, 1, 1), worker=1, datacenter=1)
    with TestClient(create_user_asgi(application)) as client:
        yield database, client


def issue(database, client):
    response = client.get("/user/requestVerificationCode", params={"phone": PHONE})
    assert response.json() == {"code": 0, "message": "success", "data": None}
    assert response.headers["content-type"] == "application/json"
    record = database.verification_codes.find_one({"phone": PHONE})
    assert len(record["code"]) == 4 and record["code"].isascii() and record["code"].isdigit()
    return record


def test_real_generators_complete_login_profile_and_relogin_without_wallet_reset(composed):
    database, client = composed
    record = issue(database, client)
    assert issue(database, client) == record
    response = client.get("/user/submitVerificationCode", params={"phone": PHONE, "code": record["code"]})
    assert response.status_code == 200 and response.json()["code"] == 0
    user = response.json()["data"]
    assert user["id"].startswith("u_") and user["id"][2:].isdigit()
    assert user["token"].isdigit() and len(user["token"]) == 19
    assert database.verification_codes.count_documents({}) == 0
    token = user["token"]
    profile = client.get("/user/getMyProfile", headers={"token": token}).json()
    assert profile["data"]["token"] is None
    updated = client.post("/user/updateProfile", headers={"token": token}, json={"nickname": "fixture", "bio": None})
    assert updated.json() == {"code": 0, "message": "success", "data": None}
    assert client.get("/user/getMyProfile", headers={"token": token}).json()["data"]["nickname"] == "fixture"
    wallet = database.wallet.find_one()
    record = issue(database, client)
    second = client.get("/user/submitVerificationCode", params={"phone": PHONE, "code": record["code"]}).json()
    assert second["data"]["id"] == user["id"]
    assert second["data"]["token"] != token
    assert database.wallet.find_one() == wallet
    assert client.get("/user/getUserByToken", params={"token": token}).json()["code"] == 13
    assert database.exceptionLog.count_documents({}) == 0


def test_absent_wrong_and_prod_bypass_keep_legacy_error_logs_and_request_completion(composed):
    database, client = composed
    assert client.get("/user/submitVerificationCode", params={"phone": PHONE, "code": "111"}).json()["code"] == 12
    database.verification_codes.insert_one({"phone": PHONE, "code": "1234"})
    assert client.get("/user/submitVerificationCode", params={"phone": PHONE, "code": "111"}).json()["code"] == 11
    assert client.get("/user/submitVerificationCode", params={"phone": PHONE, "code": "9999"}).json()["code"] == 11
    assert database.verification_codes.count_documents({}) == 1
    assert database.user.count_documents({}) == database.wallet.count_documents({}) == 0
    assert [record["systemErrorCode"] for record in database.exceptionLog.find()] == [12, 11, 11]
    assert database.requestLog.count_documents({}) == 3
    assert all("exceptionMessage" not in record for record in database.exceptionLog.find())


def test_required_binding_checks_first_parameter_and_preserves_auth_precedence(composed):
    database, client = composed
    missing = client.get("/user/requestVerificationCode")
    assert missing.status_code == 200
    assert missing.json() == {"code": 1, "message": "未知错误", "data": None}
    assert database.verification_codes.count_documents({}) == 0
    assert database.exceptionLog.count_documents({}) == database.requestLog.count_documents({}) == 1
    before = database.requestLog.count_documents({})
    assert client.post("/user/updateProfile", content=b"broken").status_code == 401
    assert database.requestLog.count_documents({}) == before
    client.get("/user/requestVerificationCode?phone=00000000000&phone=other-fixture")
    assert database.verification_codes.find_one()["phone"] == PHONE


@pytest.mark.parametrize("kind,field", [("client", "clientId"), ("session", "sessionId")])
def test_composed_watching_routes_use_shared_application_and_actual_peer(composed, kind, field):
    database, client = composed
    path = "/" + kind + "/request" + kind.capitalize() + "Id"
    response = client.get(path, headers={"User-Agent": "fixture-agent", "X-Forwarded-For": "192.0.2.1"})
    identity = response.json()["data"][field]
    assert database[kind].find_one({"_id": ObjectId(identity)})["ip"] == "testclient"
    assert database.requestLog.count_documents({}) == 1


def test_exception_log_storage_failure_is_visible_without_replaying_login(composed):
    database, _ = composed
    provider_calls = []

    class BrokenErrors:
        def insert_one(self, document):
            provider_calls.append("error-write")
            raise RuntimeError("fixture error log write failure")

    class Provider:
        def __getattr__(self, name):
            return getattr(database, name)

        def get_collection(self, name, **kwargs):
            return BrokenErrors() if name == "exceptionLog" else database.get_collection(name, **kwargs)

    application = build_user_application(Provider, NoMedia(), profile="prod", local_clock=datetime.now,
                                         worker=1, datacenter=1)
    with TestClient(create_user_asgi(application), raise_server_exceptions=False) as client:
        response = client.get("/user/submitVerificationCode", params={"phone": PHONE, "code": "111"})
    assert response.status_code == 500
    assert provider_calls == ["error-write"]
    assert database.user.count_documents({}) == 0


@pytest.mark.parametrize("case", ["token-found", "token-missing", "id-found", "id-missing"])
def test_composed_user_read_http_matches_existing_java_goldens(composed, case):
    import json

    database, client = composed
    database.user.insert_one(golden_document())
    if case.startswith("token"):
        path = "/user/getUserByToken"
        params = {"token": "fixture-legacy-token" if case.endswith("found") else "fixture-missing"}
    else:
        path = "/user/getUserById"
        params = {"userId": "fixture-user-reads" if case.endswith("found") else "fixture-missing"}
    response = client.get(path, params=params)
    expected = json.loads((ROOT / "contracts/user-read" / (case + ".json")).read_text())
    assert comparison.compare(expected, {"status": response.status_code,
                                         "contentType": response.headers["content-type"],
                                         "body": response.json()})["match"]
