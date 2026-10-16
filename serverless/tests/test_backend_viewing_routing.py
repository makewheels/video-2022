"""One explicit isolated backend for the old and new viewing paths, with one request cycle each."""

import json
from concurrent.futures import ThreadPoolExecutor
from datetime import UTC, datetime

import pytest
from bson import ObjectId
from starlette.testclient import TestClient
from test_playlist_http import task
from test_user_mongo_reads import golden_document
from test_user_mongo_reads import isolated_database as isolated_database
from test_watch_info import Urls

from video_serverless.adapters.cached_ip_locations import CachedIpLocations
from video_serverless.adapters.mongo_ip_cache import MongoIpCache
from video_serverless.adapters.user_asgi import create_user_asgi
from video_serverless.backend_bootstrap import build_backend_application
from video_serverless.ports.request_identity import RequestMetadata


class IpApi:
    calls = 0

    def lookup(self, ip):
        self.calls += 1
        return {"result": {"province": "fixture", "city": "fixture"}}


def build(provider, **options):
    return build_backend_application(provider, Urls(), profile="prod", local_clock=datetime.now,
                                     display_timezone=UTC, worker=1, datacenter=1, **options)


@pytest.fixture
def composed(isolated_database):
    database = isolated_database
    for name in database.list_collection_names():
        database[name].drop()
    user = golden_document()
    database.user.insert_one(user)
    database.video.insert_one({"_id": "fixture-video", "watch": {"watchId": "fixture-watch", "watchCount": 0},
                               "visibility": "PRIVATE", "uploaderId": user["_id"],
                               "title": "fixture", "status": "READY"})
    task(database)
    database.ip_cache.create_index("ip", unique=True)
    api = IpApi()
    locations = CachedIpLocations(MongoIpCache(lambda: database), api, lambda: datetime.now(UTC))
    application = build(lambda: database, playback_signature_secret="fixture-secret",
                        internal_base_url="https://example.invalid", ip_locations=locations)
    with TestClient(create_user_asgi(application)) as client:
        yield database, api, application, client, user


def test_all_capabilities_share_one_cycle_and_no_implicit_ip_supplier():
    application = build(lambda: pytest.fail("Assembly and unknown routes must not access Mongo"))
    viewing = application.viewing
    assert viewing.watch_logs is None
    assert viewing.history.cycle is viewing.heartbeats.cycle is viewing.playback.cycle is application.users.cycle
    with TestClient(create_user_asgi(application)) as client:
        assert client.get("/watchController/addWatchLog?videoId=fixture-video&videoStatus=READY").status_code == 404
        assert client.get("/unknown").status_code == 404


def test_hls_watch_log_both_heartbeats_progress_history_and_exit_use_one_backend(composed):
    database, api, application, client, user = composed
    cycle = application.users.cycle
    assert application.watch.cycle is application.watch.playlists.cycle is application.viewing.watch_logs.cycle is cycle
    headers = {"token": user["token"]}
    info = client.get("/watchController/getWatchInfo?watchId=fixture-watch&clientId=c&sessionId=s", headers=headers)
    assert info.json()["code"] == 0
    master = client.get(info.json()["data"]["multivariantPlaylistUrl"])
    assert master.status_code == 200 and "#EXT-X-STREAM-INF" in master.text
    child = client.get(master.text.splitlines()[-1])
    assert child.status_code == 200 and "/file/access?" in child.text
    log = client.get("/watchController/addWatchLog?videoId=fixture-video&videoStatus=READY&clientId=c&sessionId=s",
                     headers=headers)
    assert log.json()["code"] == 0
    assert api.calls == database.ip_cache.count_documents({}) == database.watchLog.count_documents({}) == 1
    assert database.video.find_one()["watch"]["watchCount"] == 1
    session = client.post("/playback/start", json={"videoId": "fixture-video", "clientId": "c", "sessionId": "s"},
                          headers=headers).json()["data"]["playbackSessionId"]
    assert client.post("/playback/heartbeat", json={"playbackSessionId": session, "currentTimeMs": 42}
                       ).json()["code"] == 0
    assert client.post("/heartbeat/add", json={"videoId": "fixture-video", "clientId": "c", "sessionId": "s",
                                              "playerStatus": "playing", "playerTime": 42}, headers=headers
                       ).json()["code"] == 0
    progress = client.get("/progress/getProgress?videoId=fixture-video&clientId=other", headers=headers)
    assert progress.json()["data"]["progressInMillis"] == 42
    history = client.get("/watchHistory/getMyHistory", headers=headers)
    assert history.json()["data"]["total"] == 1
    assert history.json()["data"]["list"][0]["videoId"] == "fixture-video"
    assert client.post("/playback/exit", content=json.dumps({"playbackSessionId": session, "exitType": "CLOSE_TAB"}),
                       headers={"Content-Type": "text/plain"}).json()["code"] == 0
    assert database.playbackSession.find_one({"_id": ObjectId(session)})["userId"] == user["_id"]
    assert client.delete("/watchHistory/clear", headers=headers).json()["code"] == 0
    assert database.watchLog.count_documents({}) == 0
    assert database.requestLog.count_documents({}) == 10
    assert database.requestLog.count_documents({"requestPath": "/heartbeat/add"}) == 0
    assert database.exceptionLog.count_documents({}) == 0


def test_concurrent_requests_keep_token_and_body_viewer_request_local(composed):
    database, api, application, client, user = composed
    def send(index):
        request = RequestMetadata("POST", "/playback/start", "http://example.invalid/playback/start", "127.0.0.1",
                                  headers=(("Content-Type", "application/json"),) + (
                                      (("token", user["token"]),) if index % 2 else ()))
        result = application.dispatch(request, json.dumps({"clientId": str(index), "userId": "ignored"}).encode())
        return index, json.loads(result.response.body), result.journal_status
    with ThreadPoolExecutor(max_workers=4) as executor:
        results = list(executor.map(send, range(12)))
    assert len({item[1]["data"]["playbackSessionId"] for item in results}) == 12
    for index, response, journal in results:
        assert journal == "saved" and response["code"] == 0
        record = database.playbackSession.find_one({"_id": ObjectId(response["data"]["playbackSessionId"])})
        assert record["clientId"] == str(index)
        assert record.get("userId") == (user["_id"] if index % 2 else None)
    assert database.requestLog.count_documents({}) == 12
    assert database.exceptionLog.count_documents({}) == api.calls == 0
