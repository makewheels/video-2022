"""Actual ASGI metadata/lifecycle/Mongo with old Java complete JSON goldens; no entrypoint install."""

import json
from datetime import UTC, datetime

import pytest
from bson.int64 import Int64
from starlette.applications import Starlette
from starlette.responses import Response
from starlette.routing import Route
from starlette.testclient import TestClient
from test_user_mongo_reads import isolated_database as isolated_database
from test_user_reads import ROOT

from video_serverless.adapters.asgi_metadata import from_asgi_metadata
from video_serverless.adapters.mongo_progress import MongoProgressStore
from video_serverless.adapters.mongo_request_journal import MongoRequestJournal
from video_serverless.adapters.mongo_users import MongoUserReads
from video_serverless.adapters.mongo_watch_history import MongoHistoryCoverUrls, MongoHistoryStore
from video_serverless.business.request_identity import IdentityResolver
from video_serverless.business.watch_history import WatchHistory
from video_serverless.history_progress_application import HISTORY_PROGRESS_ROUTES, HistoryProgressApplication
from video_serverless.request_cycle import RequestCycle


class FixtureUrls:
    def sign(self, key, seconds):
        raise AssertionError("No fixture video has a cover")


class ObservedHistory(MongoHistoryStore):
    def __init__(self, provider):
        super().__init__(provider)
        self.clear_calls = 0

    def clear(self, viewer_id):
        self.clear_calls += 1
        super().clear(viewer_id)


@pytest.fixture
def composed(isolated_database):
    database = isolated_database
    for name in ("user", "progress", "watchLog", "video", "requestLog"):
        database[name].delete_many({})
    database.progress.insert_one({"_id": "fixture-progress", "videoId": "fixture-video", "clientId": "fixture-client",
                                  "progressInMillis": Int64(42), "createTime": datetime(1970, 1, 1, tzinfo=UTC),
                                  "updateTime": datetime(1970, 1, 1, 0, 0, 1, tzinfo=UTC)})
    database.watchLog.insert_one({"_id": "fixture-log", "videoId": "fixture-video",
                                  "createTime": datetime(1970, 1, 1, 0, 0, 1, tzinfo=UTC)})
    database.video.insert_one({"_id": "fixture-video", "title": "fixture-title"})
    store = ObservedHistory(lambda: database)
    cycle = RequestCycle(IdentityResolver(MongoUserReads(lambda: database)),
                         MongoRequestJournal(lambda: database), lambda: 1000)
    history = WatchHistory(store, MongoHistoryCoverUrls(lambda: database, FixtureUrls()))
    application = HistoryProgressApplication(MongoProgressStore(lambda: database), history, cycle)

    def endpoint(request):
        result = application.dispatch(from_asgi_metadata(request))
        return Response(result.response.body, status_code=result.response.status, headers=result.response.headers,
                        background=None)

    routes = [Route(path, endpoint, methods=["GET", "POST", "DELETE"]) for path in HISTORY_PROGRESS_ROUTES]
    app = Starlette(routes=routes)
    with TestClient(app) as client:
        yield database, store, application, client


@pytest.mark.parametrize("case", ["progress-present", "progress-missing", "history-present", "history-clear"])
def test_complete_json_and_date_null_shape_matches_actual_java_http(composed, case):
    database, store, application, client = composed
    if case.startswith("progress"):
        video = "fixture-video" if case.endswith("present") else "absent"
        response = client.get("/progress/getProgress", params={"videoId": video, "clientId": "fixture-client"})
    elif case == "history-clear":
        response = client.delete("/watchHistory/clear")
    else:
        response = client.get("/watchHistory/getMyHistory")
    expected = json.loads((ROOT / "contracts/playback-read" / (case + ".json")).read_text())
    assert {"status": response.status_code, "contentType": response.headers["content-type"],
            "body": response.json()} == expected
    assert database.requestLog.count_documents({}) == 1
    assert database.requestLog.find_one()["response"]["httpStatus"] == 200
    assert store.clear_calls == (1 if case == "history-clear" else 0)


def test_query_client_is_first_value_and_header_client_is_not_a_fallback(composed):
    database, store, application, client = composed
    response = client.get("/progress/getProgress?videoId=fixture-video&clientId=fixture-client&clientId=other")
    assert response.json()["data"]["progressInMillis"] == 42
    response = client.get("/progress/getProgress?videoId=fixture-video", headers={"clientId": "fixture-client"})
    assert response.status_code == 200 and response.json()["code"] == 1
    assert database.requestLog.count_documents({}) == 2


@pytest.mark.parametrize("value", ["bad", "2147483648", "1.5"])
def test_invalid_page_is_legacy_error_and_journaled(composed, value):
    database, store, application, client = composed
    original = database.watchLog.find_one()
    response = client.get("/watchHistory/getMyHistory", params={"page": value})
    assert response.status_code == 200 and response.json()["code"] == 1
    assert database.watchLog.find_one() == original
    assert database.requestLog.count_documents({}) == 1


def test_authenticated_viewer_overrides_client_and_then_anonymous_has_no_residual_identity(composed):
    database, store, application, client = composed
    database.user.insert_one({"_id": "fixture-viewer", "token": "fixture-history-token"})
    database.progress.insert_one({"_id": "viewer-progress", "videoId": "fixture-video", "viewerId": "fixture-viewer",
                                  "clientId": "other", "progressInMillis": Int64(99)})
    url = "/progress/getProgress?videoId=fixture-video&clientId=fixture-client"
    assert client.get(url, headers={"token": "fixture-history-token"}).json()["data"]["progressInMillis"] == 99
    assert client.get(url).json()["data"]["progressInMillis"] == 42
    authenticated = client.get("/watchHistory/getMyHistory", headers={"token": "fixture-history-token"})
    assert authenticated.json()["data"]["total"] == 0
    assert client.get("/watchHistory/getMyHistory").json()["data"]["total"] == 1


def test_clear_success_survives_journal_failure_without_business_replay(composed):
    from dataclasses import replace

    from video_serverless.ports.request_identity import RequestMetadata

    database, store, application, client = composed

    class Unavailable:
        calls = 0

        def save(self, entry):
            self.calls += 1
            raise RuntimeError("synthetic journal failure")

    journal = Unavailable()
    application = replace(application, cycle=replace(application.cycle, journal=journal))
    request = RequestMetadata("DELETE", "/watchHistory/clear", "http://example.invalid/watchHistory/clear", "127.0.0.1")
    result = application.dispatch(request)
    assert result.response.status == 200 and json.loads(result.response.body)["code"] == 0
    assert result.journal_status == "failed"
    assert journal.calls == store.clear_calls == 1
    assert database.watchLog.count_documents({}) == 0


def test_wrong_method_is_error_and_unknown_route_does_not_start_lifecycle(composed):
    from video_serverless.ports.request_identity import RequestMetadata

    database, store, application, client = composed
    response = client.post("/watchHistory/clear")
    assert response.status_code == 200 and response.json()["code"] == 1
    assert store.clear_calls == 0
    assert database.requestLog.count_documents({}) == 1
    request = RequestMetadata("GET", "/unknown", "http://example.invalid/unknown", "127.0.0.1")
    result = application.dispatch(request)
    assert result.response.status == 404 and result.journal_status == "not_started"
    assert database.requestLog.count_documents({}) == 1
