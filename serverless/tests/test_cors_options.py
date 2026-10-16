"""Actual isolated ASGI/Mongo against legacy OPTIONS/CORS responses and lifecycle counts."""

import json
from datetime import UTC, datetime

import pytest
from starlette.testclient import TestClient
from test_user_mongo_reads import isolated_database as isolated_database
from test_user_reads import ROOT
from test_watch_info import Urls

from video_serverless.adapters.user_asgi import create_user_asgi
from video_serverless.backend_bootstrap import build_backend_application

CASES = json.loads((ROOT / "contracts/cors-options/cases.json").read_text(encoding="utf-8"))


@pytest.fixture
def composed(isolated_database):
    database = isolated_database
    for name in database.list_collection_names():
        database[name].drop()
    application = build_backend_application(lambda: database, Urls(), profile="prod", local_clock=datetime.now,
                                             display_timezone=UTC, worker=1, datacenter=1)
    with TestClient(create_user_asgi(application)) as client:
        yield database, application, client


def response_headers(response):
    headers = {}
    for name in response.headers:
        if name == "content-length":
            continue
        values = response.headers.get_list(name)
        if name == "vary":
            values = [part.strip() for value in values for part in value.split(",")]
        headers[name] = values
    return headers


@pytest.mark.parametrize("case", CASES, ids=[str(index) for index in range(len(CASES))])
def test_legacy_preflight_options_response_and_mutation_counts(composed, case):
    database, application, client = composed
    headers = {} if case["wanted"] == "none" else {
        "Origin": "https://fixture.invalid", "Access-Control-Request-Method": case["wanted"],
        "Access-Control-Request-Headers": "token, content-type"}
    response = client.options(case["path"], headers=headers)
    assert {"path": case["path"], "wanted": case["wanted"], "status": response.status_code,
            "body": response.text, "headers": response_headers(response),
            "requestLogs": database.requestLog.count_documents({}),
            "exceptionLogs": database.exceptionLog.count_documents({}),
            "clients": database["client"].count_documents({}),
            "sessions": database.playbackSession.count_documents({})} == case


def test_client_origin_only_is_allowed_without_global_cors_or_duplicate_business(composed):
    database, application, client = composed
    response = client.get("/client/requestClientId", headers={"Origin": "https://fixture.invalid"})
    assert response.status_code == 200 and response.json()["code"] == 0
    assert response.headers["access-control-allow-origin"] == "*"
    assert response.headers.get("access-control-allow-credentials") is None
    assert response_headers(response)["vary"] == ["Origin", "Access-Control-Request-Method",
                                                 "Access-Control-Request-Headers"]
    assert database["client"].count_documents({}) == database.requestLog.count_documents({}) == 1
    response = client.post("/playback/start", json={}, headers={"Origin": "https://fixture.invalid"})
    assert response.json()["code"] == 0 and "access-control-allow-origin" not in response.headers
    assert database.playbackSession.count_documents({}) == 1


def test_same_origin_client_has_no_added_cross_origin_headers(composed):
    database, application, client = composed
    response = client.get("/client/requestClientId", headers={"Origin": "http://testserver"})
    assert response.json()["code"] == 0 and "access-control-allow-origin" not in response.headers


def test_head_uses_get_handler_and_journals_head_without_error(composed):
    database, application, client = composed
    get = client.get("/video/getPublicVideoList?skip=0&limit=20")
    head = client.head("/video/getPublicVideoList?skip=0&limit=20")
    assert get.json()["code"] == 0 and head.status_code == 200 and head.content == b""
    assert head.headers["content-type"] == get.headers["content-type"]
    assert head.headers["content-length"] == get.headers["content-length"]
    assert database.requestLog.count_documents({}) == 2
    assert database.requestLog.count_documents({"request.method": "HEAD"}) == 1
    assert database.exceptionLog.count_documents({}) == 0


def test_options_unknown_and_unconfigured_routes_never_start_lifecycle(composed):
    database, application, client = composed
    for path in ("/unknown", "/watchController/getWatchInfo", "/watchController/addWatchLog", "/user/createAvatarFile"):
        assert client.options(path).status_code == 404
    assert database.requestLog.count_documents({}) == database.exceptionLog.count_documents({}) == 0
