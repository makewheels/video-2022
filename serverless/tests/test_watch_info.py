from datetime import datetime
from urllib.parse import parse_qs, urlsplit

import pytest
from starlette.testclient import TestClient
from test_user_mongo_reads import golden_document
from test_user_mongo_reads import isolated_database as isolated_database

from video_serverless.adapters.hmac_access_signatures import HmacAccessSignatures
from video_serverless.adapters.user_asgi import create_user_asgi
from video_serverless.ports.playback_signatures import AccessClaim
from video_serverless.user_bootstrap import build_user_application
from video_serverless.watch_bootstrap import build_watch_application

NOW = 1700000000000


class Urls:
    def __init__(self):
        self.calls = []

    def sign(self, key, seconds):
        self.calls.append((key, seconds))
        return "https://example.invalid/fixture-cover"


@pytest.fixture
def composed(isolated_database):
    database = isolated_database
    for name in ("video", "user", "progress", "cover", "requestLog", "exceptionLog", "client", "session"):
        database[name].delete_many({})
    database.user.insert_one(golden_document())
    database.video.insert_one({"_id": "fixture-video", "watch": {"watchId": "fixture-watch"},
                               "visibility": "PUBLIC", "status": "CREATED", "coverId": "fixture-cover",
                               "uploaderId": "fixture-user-reads"})
    database.cover.insert_one({"_id": "fixture-cover", "key": "fixture/cover"})
    urls = Urls()
    user_app = build_user_application(lambda: database, urls, profile="prod", local_clock=datetime.now,
                                      worker=1, datacenter=1)
    app = build_watch_application(lambda: database, urls, user_app.cycle, signature_secret="fixture-secret",
                                  internal_base_url="https://example.invalid", clock_ms=lambda: NOW,
                                  nano_id=lambda: "fixture-nano")
    with TestClient(create_user_asgi(app)) as client:
        yield database, urls, client


def request(client, params=None, headers=None):
    return client.get("/watchController/getWatchInfo", params={"watchId": "fixture-watch", **(params or {})},
                      headers=headers or {})


def test_public_watch_info_preserves_fields_ids_status_progress_and_playlist_signature(composed):
    database, urls, client = composed
    database.progress.insert_one({"videoId": "fixture-video", "clientId": "fixture-client", "progressInMillis": 1234})
    response = request(client, {"clientId": "fixture-client", "sessionId": "fixture-session", "videoId": "wrong"})
    assert response.status_code == 200 and response.headers["content-type"] == "application/json"
    data = response.json()["data"]
    assert set(data) == {"videoId", "coverUrl", "multivariantPlaylistUrl", "videoStatus", "progressInMillis"}
    assert data["videoId"] == "fixture-video" and data["videoStatus"] == "CREATED"
    assert data["progressInMillis"] == 1234
    assert urls.calls == [("fixture/cover", 7200)]
    query = parse_qs(urlsplit(data["multivariantPlaylistUrl"]).query)
    assert query["videoId"] == ["fixture-video"]
    assert query["clientId"] == ["fixture-client"] and query["sessionId"] == ["fixture-session"]
    claim = AccessClaim("fixture-video", "fixture-client", "fixture-session", "", "playlist", str(NOW), "fixture-nano")
    assert HmacAccessSignatures("fixture-secret", lambda: NOW).verify(claim, query["playbackSign"][0])
    assert database.requestLog.count_documents({}) == 1


def test_missing_context_uses_ephemeral_nanoids_and_never_creates_viewing_records(composed):
    database, _, client = composed
    data = request(client, headers={"clientId": "ignored-header", "sessionId": "ignored-header"}).json()["data"]
    query = parse_qs(urlsplit(data["multivariantPlaylistUrl"]).query)
    assert query["clientId"] == query["sessionId"] == ["fixture-nano"]
    assert data["progressInMillis"] == 0
    assert database["client"].count_documents({}) == database["session"].count_documents({}) == 0


def test_logged_in_progress_prefers_viewer_id_and_keeps_stored_null(composed):
    database, _, client = composed
    database.progress.insert_one({"videoId": "fixture-video", "clientId": "fixture-client", "progressInMillis": 1234})
    database.progress.insert_one({"videoId": "fixture-video", "viewerId": "fixture-user-reads",
                                  "progressInMillis": None})
    data = request(client, {"clientId": "fixture-client"}, {"token": "fixture-legacy-token"}).json()["data"]
    assert data["progressInMillis"] is None


def test_private_nonowner_denies_before_cover_progress_or_signed_url(composed):
    database, urls, client = composed
    database.video.update_one({}, {"$set": {"visibility": "PRIVATE"}})
    response = request(client)
    assert response.json() == {"code": 1, "message": "该视频为私密视频", "data": None}
    assert urls.calls == [] and database.exceptionLog.count_documents({}) == 0
    assert request(client, headers={"token": "fixture-legacy-token"}).json()["code"] == 0


def test_absent_watch_uses_controller_exception_with_original_error_and_detail(composed):
    database, _, client = composed
    response = request(client, {"watchId": "fixture-missing"})
    assert response.json() == {"code": 22, "message": "视频不存在", "data": None}
    log = database.exceptionLog.find_one()
    assert log["systemErrorCode"] == 22
    assert log["exceptionMessage"] == "视频watchId不存在, watchId = fixture-missing"


def test_missing_cover_key_returns_null_without_attempting_signature(composed):
    database, urls, client = composed
    database.cover.delete_many({})
    assert request(client).json()["data"]["coverUrl"] is None
    assert urls.calls == []
