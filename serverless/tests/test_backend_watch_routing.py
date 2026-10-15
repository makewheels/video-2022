"""Opt-in root application must preserve signed playback and the existing route set."""

from datetime import UTC, datetime
from urllib.parse import parse_qs, urlsplit

import pytest
from starlette.testclient import TestClient
from test_playlist_http import task
from test_user_mongo_reads import golden_document
from test_user_mongo_reads import isolated_database as isolated_database
from test_watch_info import Urls

from video_serverless.adapters.user_asgi import create_user_asgi
from video_serverless.backend_bootstrap import build_backend_application


def build(provider, **options):
    return build_backend_application(provider, Urls(), profile="prod", local_clock=datetime.now,
                                     display_timezone=UTC, worker=1, datacenter=1, **options)


@pytest.mark.parametrize("options", [
    {"playback_signature_secret": "fixture-secret"},
    {"internal_base_url": "https://example.invalid"},
    {"playback_signature_secret": "", "internal_base_url": "https://example.invalid"},
    {"playback_signature_secret": "fixture-secret", "internal_base_url": ""},
])
def test_incomplete_configuration_fails_before_database_access(options):
    def forbidden():
        pytest.fail("Invalid configuration must fail before database access")
    with pytest.raises(ValueError, match="Playback"):
        build(forbidden, **options)


def test_default_factory_keeps_playback_uninstalled():
    app = build(lambda: pytest.fail("Unused routes must not access Mongo"))
    with TestClient(create_user_asgi(app)) as client:
        for path in ("/watchController/getWatchInfo", "/watchController/getMultivariantPlaylist.m3u8",
                     "/watchController/getM3u8Content.m3u8"):
            assert client.get(path).status_code == 404


def test_root_factory_private_playback_chain_and_user_routes_share_one_cycle(isolated_database):
    database = isolated_database
    user = golden_document()
    database.user.insert_one(user)
    database.video.insert_one({"_id": "fixture-video", "watch": {"watchId": "fixture-watch"},
                               "visibility": "PRIVATE", "uploaderId": user["_id"]})
    task(database)
    before = database.video.find_one()
    before_user = database.user.find_one()
    app = build(lambda: database, playback_signature_secret="fixture-secret",
                internal_base_url="https://example.invalid")
    assert app.watch.cycle is app.users.cycle
    assert app.watch.playlists.cycle is app.users.cycle
    with TestClient(create_user_asgi(app)) as client:
        info = client.get("/watchController/getWatchInfo", params={"watchId": "fixture-watch"},
                          headers={"token": user["token"]})
        assert info.json()["code"] == 0
        master = client.get(info.json()["data"]["multivariantPlaylistUrl"])
        assert master.status_code == 200 and "#EXT-X-STREAM-INF" in master.text
        child = client.get(master.text.splitlines()[-1])
        assert child.status_code == 200 and child.text.startswith("#EXTM3U\r\nhttps://example.invalid/file/access?")
        query = parse_qs(urlsplit(child.text.splitlines()[-1]).query)
        assert query["videoId"] == ["fixture-video"] and "sign" in query
        assert database.requestLog.count_documents({}) == 3
        denied = client.get("/watchController/getMultivariantPlaylist.m3u8", params={"videoId": "fixture-video"})
        assert denied.json()["code"] == 35
        profile = client.get("/user/getUserByToken", params={"token": user["token"]})
        assert profile.json()["code"] == 0
    assert database.requestLog.count_documents({}) == 5
    assert database.exceptionLog.count_documents({}) == 1
    assert database.video.find_one() == before
    assert database.user.find_one() == before_user
    assert database.get_collection("client").count_documents({}) == 0
