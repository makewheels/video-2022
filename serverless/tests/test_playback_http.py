"""Actual isolated ASGI/Mongo compared with Spring and Fastjson HTTP/BSON snapshots."""

import json
from dataclasses import replace
from datetime import UTC, datetime

import pytest
from bson import ObjectId
from starlette.testclient import TestClient
from test_user_mongo_reads import isolated_database as isolated_database
from test_user_reads import ROOT

from video_serverless.adapters.mongo_playback_sessions import MongoPlaybackSessionStore
from video_serverless.adapters.user_asgi import create_user_asgi
from video_serverless.business.playback_sessions import PlaybackSessions
from video_serverless.playback_application import PlaybackApplication
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.user_bootstrap import build_user_application

NOW = datetime(2024, 1, 2, 3, 4, 5, tzinfo=UTC)
CASES = json.loads((ROOT / "contracts/playback-json/cases.json").read_text(encoding="utf-8"))


@pytest.fixture
def composed(isolated_database):
    database = isolated_database
    for name in ("playbackSession", "user", "requestLog", "exceptionLog"):
        database[name].delete_many({})
    cycle = build_user_application(lambda: database, None, profile="prod", local_clock=datetime.now,
                                   worker=1, datacenter=1).cycle
    application = PlaybackApplication(PlaybackSessions(MongoPlaybackSessionStore(lambda: database), lambda: NOW), cycle)
    with TestClient(create_user_asgi(application)) as client:
        yield database, application, client


def seed(database):
    database.playbackSession.insert_one({"_id": "fixture-session", "videoId": "fixture-video", "heartbeatCount": 0,
                                         "totalPlayDurationMs": 0, "maxProgressMs": 0, "currentProgressMs": 0,
                                         "resolution": "auto", "exitType": "PLAYING", "startTime": NOW,
                                         "createTime": NOW, "updateTime": NOW})


def call(client, route, body, media="application/json", headers=None):
    return client.post("/playback/" + route, content=body.encode(), headers={"Content-Type": media, **(headers or {})})


def normalize(document):
    if document is None:
        return None
    document.pop("_id")
    return {name: "present" if isinstance(value, datetime) else value for name, value in document.items()}


@pytest.mark.parametrize("case", CASES, ids=[str(index) for index in range(len(CASES))])
def test_http_response_document_and_journals_match_actual_legacy_http(composed, case):
    database, application, client = composed
    if case["route"] != "start":
        seed(database)
    response = client.request(case["method"], "/playback/" + case["route"], content=case["input"].encode(),
                              headers={"Content-Type": case["media"]})
    body = response.json()
    if case["route"] == "start" and body["code"] == 0:
        assert body["data"]["playbackSessionId"] == str(database.playbackSession.find_one()["_id"])
        body["data"]["playbackSessionId"] = "generated"
    assert {"route": case["route"], "input": case["input"], "media": case["media"], "method": case["method"],
            "status": response.status_code, "contentType": response.headers["content-type"], "response": body,
            "saved": normalize(database.playbackSession.find_one()),
            "requestLogs": database.requestLog.count_documents({}),
            "exceptionLogs": database.exceptionLog.count_documents({})} == case


def test_login_start_then_anonymous_update_and_exit_then_heartbeat(composed):
    database, application, client = composed
    database.user.insert_one({"_id": "viewer", "token": "fixture-token"})
    response = call(client, "start", '{"userId":"body-user","videoId":"unknown","clientId":"c"}',
                    headers={"token": "fixture-token"})
    session = response.json()["data"]["playbackSessionId"]
    command = json.dumps({"playbackSessionId": session, "currentTimeMs": 80, "exitType": "custom"})
    assert call(client, "exit", command, "text/plain").json()["code"] == 0
    ended = database.playbackSession.find_one()["endTime"]
    assert call(client, "heartbeat", command).json()["code"] == 0
    stored = database.playbackSession.find_one()
    assert stored["userId"] == "viewer" and stored["endTime"] == ended and stored["exitType"] == "custom"
    assert stored["heartbeatCount"] == 1 and stored["currentProgressMs"] == stored["maxProgressMs"] == 80
    response = call(client, "start", '{}')
    assert "userId" not in database.playbackSession.find_one({"_id": ObjectId(
        response.json()["data"]["playbackSessionId"])})
    assert database.requestLog.count_documents({}) == 4


def test_request_journal_failure_does_not_start_second_session(composed):
    database, application, client = composed

    class Unavailable:
        calls = 0

        def save(self, entry):
            self.calls += 1
            raise RuntimeError("synthetic request journal failure")

    journal = Unavailable()
    application = replace(application, cycle=replace(application.cycle, journal=journal))
    request = RequestMetadata("POST", "/playback/start", "http://example.invalid/playback/start", "127.0.0.1",
                              headers=(("Content-Type", "application/json"),))
    result = application.dispatch(request, b"{}")
    assert result.response.status == 200 and json.loads(result.response.body)["code"] == 0
    assert result.journal_status == "failed" and journal.calls == database.playbackSession.count_documents({}) == 1
