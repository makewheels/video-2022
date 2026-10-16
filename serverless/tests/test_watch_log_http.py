"""Actual isolated HTTP/Mongo writes and lifecycle; IP outcomes are synthetic."""

import json
from dataclasses import replace
from datetime import UTC, datetime

import pytest
from starlette.testclient import TestClient
from test_user_mongo_reads import isolated_database as isolated_database
from test_watch_log_mongo import INFO, Locations

from video_serverless.adapters.mongo_watch_log import MongoWatchLogStore
from video_serverless.adapters.user_asgi import create_user_asgi
from video_serverless.business.watch_log import WatchLogs
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.user_bootstrap import build_user_application
from video_serverless.watch_log_application import WATCH_LOG_ROUTE, WatchLogApplication


@pytest.fixture
def composed(isolated_database):
    database = isolated_database
    for name in ("video", "watchLog", "user", "requestLog", "exceptionLog"):
        database[name].delete_many({})
    database.video.insert_one({"_id": "fixture-video", "status": "READY", "visibility": "PRIVATE",
                               "watch": {"watchCount": 0}})
    cycle = build_user_application(lambda: database, None, profile="prod", local_clock=datetime.now,
                                   worker=1, datacenter=1).cycle
    locations = Locations()
    logs = WatchLogs(MongoWatchLogStore(lambda: database), locations, lambda: datetime(2024, 1, 2, tzinfo=UTC))
    application = WatchLogApplication(logs, cycle)
    with TestClient(create_user_asgi(application), client=("127.0.0.2", 50000)) as client:
        yield database, locations, application, client


def call(client, **kwargs):
    return client.get(WATCH_LOG_ROUTE, params={"videoId": "fixture-video", "videoStatus": "READY",
                                              "clientId": "client", "sessionId": "session"}, **kwargs)


def assert_response(response, code=0, message="success"):
    assert response.status_code == 200
    assert response.headers["content-type"] == "application/json"
    assert response.json() == {"code": code, "message": message, "data": None}


def count(database):
    return database.video.find_one()["watch"]["watchCount"]


def test_anonymous_private_video_peer_address_query_and_first_values(composed):
    database, locations, application, client = composed
    response = client.get(WATCH_LOG_ROUTE + "?videoId=fixture-video&videoStatus=READY&videoStatus=OTHER"
                          "&clientId=first&clientId=other&sessionId=first&sessionId=other",
                          headers={"clientId": "header", "sessionId": "header", "User-Agent": "fixture-agent",
                                   "X-Forwarded-For": "198.51.100.1"})
    assert_response(response)
    event = database.watchLog.find_one()
    assert event["clientId"] == event["sessionId"] == "first"
    assert event["ip"] == "127.0.0.2" and event["userAgent"] == "fixture-agent"
    assert "viewerId" not in event and count(database) == 1
    assert locations.calls == ["127.0.0.2"] * 2
    assert database.requestLog.count_documents({}) == 1


def test_header_ids_are_not_fallbacks_and_identity_does_not_leak(composed):
    database, locations, application, client = composed
    database.user.insert_one({"_id": "viewer", "token": "fixture-token"})
    url = WATCH_LOG_ROUTE + "?videoId=fixture-video&videoStatus=READY"
    assert_response(client.get(url, headers={"token": "fixture-token", "clientId": "header", "sessionId": "header"}))
    event = database.watchLog.find_one()
    assert event["viewerId"] == "viewer" and "clientId" not in event and "sessionId" not in event
    assert_response(client.get(url))  # null-session dedup is shared across viewers
    assert count(database) == 1
    database.watchLog.delete_many({})
    assert_response(client.get(url, headers={"token": "invalid"}))
    assert "viewerId" not in database.watchLog.find_one()


@pytest.mark.parametrize("query", ["", "?videoStatus=READY", "?videoId=absent&videoStatus=READY"])
def test_binding_precedes_existence_and_no_business_write_on_error(composed, query):
    database, locations, application, client = composed
    response = client.get(WATCH_LOG_ROUTE + query)
    assert_response(response, 1 if not query else 22, "未知错误" if not query else "视频不存在")
    assert count(database) == 0 and database.watchLog.count_documents({}) == 0
    assert not locations.calls and database.requestLog.count_documents({}) == 1
    assert database.exceptionLog.count_documents({}) == 1


@pytest.mark.parametrize("outcomes,events,second_count", [
    [ [RuntimeError("synthetic first failure"), INFO, INFO], 0, 2],
    [ [INFO, RuntimeError("synthetic second failure")], 1, 1],
])
def test_partial_ip_failure_returns_error_without_automatic_replay(composed, outcomes, events, second_count):
    database, locations, application, client = composed
    locations.outcomes = outcomes
    assert_response(call(client), 1, "未知错误")
    assert count(database) == 1 and database.watchLog.count_documents({}) == events
    assert database.requestLog.count_documents({}) == database.exceptionLog.count_documents({}) == 1
    assert_response(call(client))
    assert count(database) == second_count and database.watchLog.count_documents({}) == 1
    assert database.requestLog.count_documents({}) == 2


def test_mismatched_actual_status_keeps_old_repeated_count_behavior(composed):
    database, locations, application, client = composed
    database.video.update_one({}, {"$set": {"status": "TRANSCODING"}})
    assert_response(call(client))
    assert_response(call(client))
    assert count(database) == 2 and database.watchLog.count_documents({"videoStatus": "TRANSCODING"}) == 2


def test_journal_failure_keeps_success_and_never_replays_business(composed):
    database, locations, application, client = composed

    class Unavailable:
        calls = 0

        def save(self, entry):
            self.calls += 1
            raise RuntimeError("synthetic journal failure")

    journal = Unavailable()
    application = replace(application, cycle=replace(application.cycle, journal=journal))
    request = RequestMetadata("GET", WATCH_LOG_ROUTE, "http://example.invalid" + WATCH_LOG_ROUTE, "127.0.0.2",
                              parameters=(("videoId", "fixture-video"), ("videoStatus", "READY")))
    result = application.dispatch(request)
    assert json.loads(result.response.body) == {"code": 0, "message": "success", "data": None}
    assert result.journal_status == "failed" and journal.calls == count(database) == 1
    assert database.watchLog.count_documents({}) == 1 and len(locations.calls) == 2


def test_wrong_method_is_journaled_unknown_route_does_not_start_cycle(composed):
    database, locations, application, client = composed
    assert_response(client.post(WATCH_LOG_ROUTE + "?videoId=fixture-video&videoStatus=READY"), 1, "未知错误")
    assert client.get("/unknown").status_code == 404
    assert count(database) == 0 and not locations.calls
    assert database.requestLog.count_documents({}) == 1
