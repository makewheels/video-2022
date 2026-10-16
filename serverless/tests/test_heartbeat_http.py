"""Real isolated ASGI/Mongo compared with complete legacy HTTP/JSON binding snapshots."""

import json
from datetime import UTC, datetime

import pytest
from bson.decimal128 import Decimal128
from starlette.testclient import TestClient
from test_user_mongo_reads import isolated_database as isolated_database
from test_user_reads import ROOT

from video_serverless.adapters.mongo_heartbeat import MongoHeartbeatStore
from video_serverless.adapters.mongo_progress import MongoProgressStore
from video_serverless.adapters.user_asgi import create_user_asgi
from video_serverless.business.heartbeat import Heartbeats
from video_serverless.business.progress import ProgressUpdates
from video_serverless.heartbeat_application import HEARTBEAT_ROUTE, HeartbeatApplication
from video_serverless.user_bootstrap import build_user_application

NOW = datetime(2024, 1, 2, 3, 4, 5, tzinfo=UTC)
CASES = json.loads((ROOT / "contracts/heartbeat-json/cases.json").read_text(encoding="utf-8"))


@pytest.fixture
def composed(isolated_database):
    database = isolated_database
    for name in ("heartbeat", "progress", "user", "requestLog", "exceptionLog"):
        database[name].delete_many({})
    cycle = build_user_application(lambda: database, None, profile="prod", local_clock=datetime.now,
                                   worker=1, datacenter=1).cycle
    heartbeats = Heartbeats(MongoHeartbeatStore(lambda: database),
                            ProgressUpdates(MongoProgressStore(lambda: database, clock=lambda: NOW), lambda: NOW),
                            lambda: NOW)
    application = HeartbeatApplication(heartbeats, cycle)
    with TestClient(create_user_asgi(application)) as client:
        yield database, application, client


def normalized(document):
    if document is None:
        return None
    document.pop("_id")
    assert document.pop("createTime") == NOW.replace(tzinfo=None)
    result = {}
    for name, value in document.items():
        if isinstance(value, datetime):
            value = int((value.replace(tzinfo=UTC) - datetime(1970, 1, 1, tzinfo=UTC)).total_seconds() * 1000)
        if isinstance(value, Decimal128):
            value = str(value.to_decimal())
        result[name] = value
    return result


@pytest.mark.parametrize("case", CASES, ids=[str(index) for index in range(len(CASES))])
def test_http_binding_and_bson_matches_actual_java_snapshot(composed, case):
    database, application, client = composed
    response = client.post(HEARTBEAT_ROUTE, content=case["input"].encode(),
                           headers={"Content-Type": "application/json"})
    actual = {"input": case["input"], "status": response.status_code,
              "contentType": response.headers["content-type"], "response": response.json(),
              "saved": normalized(database.heartbeat.find_one())}
    assert actual == case
    assert database.requestLog.count_documents({}) == 0
    assert database.exceptionLog.count_documents({}) == (case["response"]["code"] != 0)


def test_authenticated_viewer_then_anonymous_body_viewer_and_query_is_not_a_fallback(composed):
    database, application, client = composed
    database.user.insert_one({"_id": "viewer", "token": "fixture-token"})
    body = '{"clientId":"body-client","viewerId":"body-viewer","playerStatus":"playing","playerTime":42}'
    response = client.post(HEARTBEAT_ROUTE + "?clientId=query", content=body,
                           headers={"Content-Type": "application/json", "token": "fixture-token"})
    assert response.json()["code"] == 0
    assert database.progress.find_one()["viewerId"] == "viewer"
    assert database.progress.find_one()["clientId"] == "body-client"
    assert client.post(HEARTBEAT_ROUTE, content=body, headers={"Content-Type": "application/json"}).json()["code"] == 0
    assert database.heartbeat.count_documents({"viewerId": "body-viewer"}) == 1
    assert database.progress.count_documents({}) == 2


def test_progress_failure_keeps_one_heartbeat_and_skips_request_journal(composed):
    database, application, client = composed
    response = client.post(HEARTBEAT_ROUTE, content='{"playerStatus":"playing","playerTime":42}',
                           headers={"Content-Type": "application/json"})
    assert response.status_code == 200 and response.json()["code"] == 1
    assert database.heartbeat.count_documents({}) == 1
    assert database.progress.count_documents({}) == database.requestLog.count_documents({}) == 0
    assert database.exceptionLog.count_documents({}) == 1


@pytest.mark.parametrize("method", ["GET", "POST"])
def test_unrestricted_legacy_method_and_media_type_binding(composed, method):
    database, application, client = composed
    response = client.request(method, HEARTBEAT_ROUTE, content='{"clientId":"fixture-client"}',
                              headers={"Content-Type": "application/json"})
    assert response.status_code == 200 and response.json()["code"] == 0
    response = client.post(HEARTBEAT_ROUTE, content='{}', headers={"Content-Type": "text/plain"})
    assert response.status_code == 200 and response.json()["code"] == 1
    assert database.heartbeat.count_documents({}) == 1
    assert database.requestLog.count_documents({}) == 0
    assert client.get("/unknown").status_code == 404


def test_explicit_id_replaces_heartbeat_and_null_omits_old_fields(composed):
    database, application, client = composed
    for content in ('{"id":"fixed","playerVolume":0.1250,"event":"old"}',
                    '{"id":"fixed","playerVolume":null}'):
        assert client.post(HEARTBEAT_ROUTE, content=content,
                           headers={"Content-Type": "application/json"}).json()["code"] == 0
    assert database.heartbeat.count_documents({}) == 1
    document = database.heartbeat.find_one()
    assert document["_id"] == "fixed" and "event" not in document and "playerVolume" not in document
