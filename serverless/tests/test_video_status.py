"""Old status HTTP snapshots against real isolated Mongo, plus authorization read races."""

import copy
import json
from datetime import UTC, datetime
from pathlib import Path

import pytest
from bson import ObjectId
from starlette.testclient import TestClient
from test_user_mongo_reads import golden_document
from test_user_mongo_reads import isolated_database as isolated_database
from test_watch_info import Urls

from video_serverless.adapters.user_asgi import create_user_asgi
from video_serverless.backend_bootstrap import build_backend_application
from video_serverless.business.video_status import VideoStatusError, VideoStatuses
from video_serverless.ports.video_status import VideoStatusSnapshot

CASES = json.loads((Path(__file__).parents[1] / "contracts/video-status/cases.json").read_text(encoding="utf-8"))


@pytest.fixture
def database(isolated_database):
    for name in ("user", "file", "video", "requestLog", "exceptionLog", "id_counters"):
        isolated_database[name].delete_many({})
    isolated_database.user.insert_one(golden_document() | {"_id": "fixture-uploader", "token": "fixture-status-token"})
    return isolated_database


def backend(database, enabled=True):
    return build_backend_application(lambda: database, Urls(), profile="prod", local_clock=datetime.now,
                                     display_timezone=UTC, worker=1, datacenter=1,
                                     creation_base_url="https://example.invalid" if enabled else None)


@pytest.mark.parametrize("case", CASES)
def test_old_status_response_and_read_only_effects_match_java(database, case):
    scenario = case["scenario"]
    document = copy.deepcopy(case["video"])
    if document is not None:
        if scenario == "object-id":
            document["_id"] = ObjectId(document["_id"])
        database.video.insert_one(copy.deepcopy(document))
    parameters = {} if scenario == "missing-parameter" else {
        "videoId": str(document["_id"]) if document is not None else "fixture-video"}
    headers = {} if scenario == "no-token" else {
        "token": "invalid" if scenario == "bad-token" else "fixture-status-token"}
    with TestClient(create_user_asgi(backend(database))) as client:
        response = client.get("/video/getVideoStatus", params=parameters, headers=headers)
    assert response.status_code == case["status"]
    assert response.headers["Content-Type"] == case["contentType"]
    assert response.json() == case["response"]
    assert database.video.find_one() == document
    assert database.requestLog.count_documents({}) == case["requestLogs"]
    assert database.exceptionLog.count_documents({}) == case["exceptionLogs"]
    assert database.file.count_documents({}) == database.id_counters.count_documents({}) == 0


def test_status_opt_in_authentication_options_head_and_original_method_log(database):
    database.video.insert_one({"_id": "fixture-video", "uploaderId": "fixture-uploader", "status": "READY"})
    with TestClient(create_user_asgi(backend(database, enabled=False))) as client:
        assert client.get("/video/getVideoStatus").status_code == 404
    application = backend(database)
    assert application.upload.statuses.cycle is application.users.cycle
    with TestClient(create_user_asgi(application)) as client:
        assert client.options("/video/getVideoStatus").status_code == 401
        headers = {"token": "fixture-status-token"}
        options = client.options("/video/getVideoStatus", headers=headers)
        assert options.status_code == 200 and options.headers["Allow"] == "GET,HEAD,OPTIONS"
        head = client.head("/video/getVideoStatus?videoId=fixture-video", headers=headers)
        assert head.status_code == 200 and head.content == b"" and head.headers["Content-Type"] == "application/json"
        assert client.post("/video/getVideoStatus?videoId=fixture-video", headers=headers).json()["code"] == 1
    logs = list(database.requestLog.find())
    assert len(logs) == 3
    assert [log["request"]["method"] for log in logs] == ["OPTIONS", "HEAD", "POST"]
    assert database.exceptionLog.count_documents({}) == 1
    assert database.video.find_one()["status"] == "READY"


@pytest.mark.parametrize("scenario,code", [("first-missing", 22), ("second-missing", 22), ("user-missing", 14),
                                          ("owner-missing", None), ("owner-null", None), ("mismatch", 23),
                                          ("last-missing", None)])
def test_authorization_read_failures_do_not_return_a_stale_or_unowned_status(scenario, code):
    exists = iter([scenario != "first-missing", scenario != "second-missing"])
    owned = VideoStatusSnapshot("v", None if scenario == "owner-null" else "other" if scenario == "mismatch" else "u",
                                "READY")
    results = iter([None if scenario == "owner-missing" else owned, None if scenario == "last-missing" else owned])

    class Reads:
        def video_exists(self, video_id):
            return next(exists)

        def user_exists(self, user_id):
            return scenario != "user-missing"

        def find(self, video_id):
            return next(results)

    with pytest.raises(VideoStatusError if code else RuntimeError) as error:
        VideoStatuses(Reads()).get("u", "v")
    if code:
        assert error.value.code == code
