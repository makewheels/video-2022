"""Old LOCAL lane HTTP, full BSON and partial persistence compared against real isolated Mongo."""

import copy
import json
from dataclasses import replace
from pathlib import Path

import pytest
from bson import Int64
from starlette.testclient import TestClient
from test_local_covers import NOW, Objects, restore
from test_user_mongo_reads import isolated_database as isolated_database
from test_watch_info import Urls

from video_serverless.adapters.mongo_local_transcodes import MongoLocalTranscodes
from video_serverless.adapters.user_asgi import create_user_asgi
from video_serverless.backend_bootstrap import build_backend_application
from video_serverless.business.local_transcodes import LocalTranscodes
from video_serverless.local_transcode_application import decode_local_transcode

CASES = json.loads((Path(__file__).parents[1] / "contracts/local-transcode/cases.json").read_text(encoding="utf-8"))


@pytest.fixture
def database(isolated_database):
    for name in ("user", "video", "file", "transcode", "requestLog", "exceptionLog", "fcTask"):
        isolated_database[name].delete_many({})
    isolated_database.user.insert_one({"_id": "fixture-uploader", "token": "fixture-local-token"})
    return isolated_database


def seed(database, case):
    for name, documents in case["before"].items():
        if documents:
            database[name].insert_many([restore(copy.deepcopy(document)) for document in documents])


def normalized(database):
    result = {}
    for name in ("video", "file", "transcode"):
        documents = list(database[name].find().sort("_id", 1))
        for document in documents:
            for field in ("createTime", "updateTime", "finishTime"):
                if document.get(field) is not None:
                    document[field] = "<server-date>"
        result[name] = documents
    return result


def backend(database, objects, enabled=True):
    application = build_backend_application(
        lambda: database, Urls(), profile="prod", local_clock=lambda: NOW, display_timezone=NOW.tzinfo,
        worker=1, datacenter=1, upload_objects=objects, local_transcode_registration=enabled,
    )
    if enabled:
        transcodes = replace(application.local_transcodes.transcodes, clock=lambda: NOW,
                             store=MongoLocalTranscodes(lambda: database, lambda: NOW))
        local = replace(application.local_transcodes, transcodes=transcodes)
        application = replace(application, local_transcodes=local)
    return application


@pytest.mark.parametrize("case", CASES, ids=lambda row: row["scenario"])
def test_local_lane_http_and_complete_documents_match_old_java(database, case):
    seed(database, case)
    objects = Objects(case["scenario"])
    with TestClient(create_user_asgi(backend(database, objects))) as client:
        headers = {"Content-Type": "application/json"}
        if case["scenario"] != "no-token":
            headers["token"] = "fixture-local-token"
        response = client.post("/transcode/local/createTranscode", content=case["input"].encode(), headers=headers)
    assert response.status_code == case["status"]
    assert response.headers["Content-Type"] == case["contentType"]
    assert response.json() == case["response"]
    assert normalized(database) == case["after"]
    assert database.requestLog.count_documents({}) == case["requestLogs"]
    assert database.exceptionLog.count_documents({}) == case["exceptionLogs"]
    assert len(objects.calls) == case["remoteCalls"]
    assert database.fcTask.count_documents({}) == 0
    video = database.video.find_one()
    if video and video.get("mediaInfo", {}).get("duration") is not None:
        assert isinstance(video["mediaInfo"]["duration"], Int64)


def test_local_transcode_routes_are_opt_in_and_completion_is_not_installed(database):
    objects = Objects()
    application = backend(database, objects)
    assert application.local_transcodes.cycle is application.users.cycle
    with TestClient(create_user_asgi(backend(database, objects, enabled=False))) as client:
        assert client.post("/transcode/local/createTranscode", json={}).status_code == 404
    with TestClient(create_user_asgi(application)) as client:
        assert client.post("/transcode/local/finishTranscode").status_code == 404
    assert not objects.calls


@pytest.mark.parametrize("failure", [1, 2, 3])
def test_lane_write_failures_do_not_retry_or_persist_video_before_all_lanes(database, failure):
    seed(database, CASES[0])
    inner = MongoLocalTranscodes(lambda: database, lambda: NOW)
    calls = []

    class FailingStore:
        def __getattr__(self, name):
            original = getattr(inner, name)
            if not name.startswith("save_"):
                return original

            def save(document):
                calls.append(name)
                if len(calls) == failure:
                    raise RuntimeError("synthetic lane persistence failure")
                original(document)
            return save

    objects = Objects()
    transcodes = LocalTranscodes(FailingStore(), objects, lambda: NOW, NOW.tzinfo)
    with pytest.raises(RuntimeError, match="synthetic lane persistence failure"):
        transcodes.create("fixture-uploader", decode_local_transcode(CASES[0]["input"].encode(), "application/json"))
    assert calls == ["save_transcode", "save_transcode", "save_video"][:failure]
    assert database.transcode.count_documents({}) == failure - 1
    assert normalized(database)["video"] == CASES[0]["before"]["video"]
    assert not objects.calls


def test_second_lane_registration_reuses_first_media_and_finished_lane(database):
    seed(database, CASES[0])
    objects = Objects()
    with TestClient(create_user_asgi(backend(database, objects))) as client:
        headers = {"token": "fixture-local-token", "Content-Type": "application/json"}
        first = client.post("/transcode/local/createTranscode", content=CASES[0]["input"], headers=headers)
        assert first.json()["code"] == 0
        before = database.video.find_one()["mediaInfo"]
        database.transcode.update_one({"resolution": "720p"}, {"$set": {"status": "FINISHED"}})
        changed = json.loads(CASES[0]["input"]) | {"resolution": "1080p", "durationMs": 123, "width": 3840}
        response = client.post("/transcode/local/createTranscode", json=changed, headers=headers)
        assert response.json()["code"] == 0
    assert database.video.find_one()["mediaInfo"] == before
    assert database.transcode.count_documents({}) == 2
    assert database.transcode.find_one({"resolution": "720p"})["status"] == "FINISHED"
    assert database.video.find_one()["status"] == "TRANSCODING"
