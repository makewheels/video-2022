"""Actual ASGI/Mongo round trips for the exact request bytes used by legacy Java converters."""

import base64
import json
from datetime import UTC, datetime

import pytest
from starlette.testclient import TestClient
from test_user_mongo_reads import isolated_database as isolated_database
from test_user_reads import ROOT
from test_watch_info import Urls

from video_serverless.adapters.user_asgi import create_user_asgi
from video_serverless.backend_bootstrap import build_backend_application

CASES = json.loads((ROOT / "contracts/body-encoding/cases.json").read_text(encoding="utf-8"))


@pytest.fixture
def composed(isolated_database):
    database = isolated_database
    for name in database.list_collection_names():
        database[name].drop()
    database.user.insert_one({"_id": "fixture-user", "token": "fixture-token", "nickname": "before", "bio": "before"})
    application = build_backend_application(lambda: database, Urls(), profile="prod", local_clock=datetime.now,
                                             display_timezone=UTC, worker=1, datacenter=1)
    with TestClient(create_user_asgi(application)) as client:
        yield database, client


def stored(database, path):
    if path.startswith("/user/"):
        document = database.user.find_one()
        names = ("nickname", "bio")
    elif path.startswith("/heartbeat/"):
        document = database.heartbeat.find_one()
        names = ("event", "clientId", "viewerId")
    else:
        document = database.playbackSession.find_one()
        names = ("videoId", "clientId") if path.endswith("/start") else ("exitType",)
    if document is None:
        return None
    result = {name: document.get(name) for name in names}
    if path.endswith("/exit"):
        result["ended"] = "endTime" in document
    return result


@pytest.mark.parametrize("case", CASES, ids=[str(index) for index in range(len(CASES))])
def test_encoded_request_response_saved_text_and_logs_match_real_java(composed, case):
    database, client = composed
    path = case["path"]
    if path.endswith("/exit"):
        database.playbackSession.insert_one({"_id": "fixture-session", "exitType": "PLAYING", "heartbeatCount": 0})
    response = client.post(path, content=base64.b64decode(case["bytes"]),
                           headers={"Content-Type": case["media"], "token": "fixture-token"})
    body = response.json()
    if path.endswith("/start") and body["code"] == 0:
        body["data"]["playbackSessionId"] = "generated"
    assert {"path": path, "bytes": case["bytes"], "media": case["media"], "status": response.status_code,
            "contentType": response.headers["content-type"], "body": body, "saved": stored(database, path),
            "requestLogs": database.requestLog.count_documents({}),
            "exceptionLogs": database.exceptionLog.count_documents({})} == case
