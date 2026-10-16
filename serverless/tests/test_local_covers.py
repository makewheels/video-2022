"""Actual isolated Mongo/ASGI comparison with old local-cover HTTP/BSON snapshots."""

import copy
import json
from dataclasses import replace
from datetime import UTC, datetime
from pathlib import Path

import pytest
from starlette.testclient import TestClient
from test_user_mongo_reads import isolated_database as isolated_database
from test_watch_info import Urls

from video_serverless.adapters.mongo_local_covers import MongoLocalCovers
from video_serverless.adapters.user_asgi import create_user_asgi
from video_serverless.backend_bootstrap import build_backend_application
from video_serverless.business.local_covers import LocalCovers
from video_serverless.ports.avatars import UploadCredentials

CASES = json.loads((Path(__file__).parents[1] / "contracts/local-cover/cases.json").read_text(encoding="utf-8"))
NOW = datetime(2024, 1, 15, tzinfo=UTC)
BASE = "https://video-2022-test.oss-cn-beijing.aliyuncs.com/"


class Ids:
    def long_id(self, prefix):
        return {"f_": "fixture-cover-file", "c_": "fixture-cover"}[prefix]


class Objects:
    def __init__(self, scenario="success"):
        self.scenario, self.calls = scenario, []

    def upload_credentials(self, key):
        self.calls.append(key)
        if self.scenario == "credentials-failure":
            raise RuntimeError("synthetic STS failure")
        if self.scenario == "credentials-null":
            return None
        return UploadCredentials("fixture-bucket", "fixture-key", "https://example.invalid",
                                 "fixture-not-real", "fixture-not-real", "fixture-not-real", "2030-01-01T00:00:00Z")


@pytest.fixture
def database(isolated_database):
    for name in ("user", "video", "cover", "file", "requestLog", "exceptionLog", "id_counters"):
        isolated_database[name].delete_many({})
    isolated_database.user.insert_one({"_id": "fixture-uploader", "token": "fixture-local-token"})
    return isolated_database


def restore(value):
    if isinstance(value, dict):
        return {name: restore(item) for name, item in value.items()}
    return NOW if value == "<server-date>" else value


def seed(database, case):
    for name, document in case["before"].items():
        if document is not None:
            database[name].insert_one(restore(copy.deepcopy(document)))


def normalized(database):
    result = {}
    for name in ("video", "file", "cover"):
        value = database[name].find_one()
        if value is not None:
            for field in ("createTime", "updateTime", "finishTime"):
                if value.get(field) is not None:
                    assert isinstance(value[field], datetime)
                    value[field] = "<server-date>"
        result[name] = value
    return result


def backend(database, objects, enabled=True):
    application = build_backend_application(
        lambda: database, Urls(), profile="prod", local_clock=lambda: NOW, display_timezone=UTC,
        worker=1, datacenter=1, upload_objects=objects,
        local_cover_access_base_url=BASE if enabled else None,
    )
    if enabled:
        covers = replace(application.local_covers.covers, ids=Ids(), clock=lambda: NOW,
                         store=MongoLocalCovers(lambda: database, lambda: NOW))
        application = replace(application, local_covers=replace(application.local_covers, covers=covers))
    return application


@pytest.mark.parametrize("case", CASES, ids=lambda case: case["operation"] + ":" + case["scenario"])
def test_old_local_cover_http_response_documents_and_side_effects(database, case):
    seed(database, case)
    objects = Objects(case["scenario"])
    headers = {} if case["scenario"] == "no-token" else {"token": "fixture-local-token"}
    with TestClient(create_user_asgi(backend(database, objects))) as client:
        if case["operation"] == "create":
            response = client.post("/cover/local/createCover", content=case["input"].encode(),
                                   headers=headers | {"Content-Type": "application/json"})
        else:
            parameters = {} if case["scenario"] == "missing-parameter" else {"coverId": "fixture-cover"}
            response = client.post("/cover/local/finishCover", params=parameters, headers=headers)
    assert response.status_code == case["status"]
    assert response.headers["Content-Type"] == case["contentType"]
    assert response.json() == case["response"]
    assert normalized(database) == case["after"]
    assert database.requestLog.count_documents({}) == case["requestLogs"]
    assert database.exceptionLog.count_documents({}) == case["exceptionLogs"]
    assert len(objects.calls) == case["remoteCalls"]
    assert database.id_counters.count_documents({}) == 0


def test_opt_in_routes_share_existing_cycle_and_disabled_routes_are_absent(database):
    objects = Objects()
    application = backend(database, objects)
    assert application.local_covers.cycle is application.users.cycle
    with TestClient(create_user_asgi(backend(database, objects, enabled=False))) as client:
        assert client.post("/cover/local/createCover", json={}).status_code == 404
        assert client.post("/cover/local/finishCover").status_code == 404
    assert not objects.calls
    with TestClient(create_user_asgi(application)) as client:
        unsupported = client.get("/cover/local/createCover")
        assert unsupported.status_code == 200 and unsupported.json()["code"] == 1
        assert database.requestLog.count_documents({}) == 0
        assert database.exceptionLog.count_documents({}) == 1
        assert client.options("/cover/local/createCover").status_code == 401
        allowed = client.options("/cover/local/createCover", headers={"token": "fixture-local-token"})
        assert allowed.status_code == 200 and allowed.headers["Allow"] == "POST,OPTIONS"
        assert database.requestLog.count_documents({}) == 1


@pytest.mark.parametrize("failure", [1, 2, 3])
def test_creation_write_failures_keep_partial_documents_without_replay(database, failure):
    seed(database, CASES[0])
    inner = MongoLocalCovers(lambda: database, lambda: NOW)
    calls = []

    class FailingStore:
        def __getattr__(self, name):
            original = getattr(inner, name)
            if not name.startswith("save_"):
                return original

            def save(document):
                calls.append(name)
                if len(calls) == failure:
                    raise RuntimeError("synthetic persistence failure")
                original(document)
            return save

    objects = Objects()
    covers = LocalCovers(FailingStore(), objects, Ids(), lambda: NOW, UTC, BASE)
    with pytest.raises(RuntimeError, match="synthetic persistence failure"):
        covers.create("fixture-uploader", "fixture-video", None)
    assert calls == ["save_file", "save_cover", "save_file"][:failure]
    assert database.file.count_documents({}) == int(failure > 1)
    assert database.cover.count_documents({}) == int(failure > 2)
    if failure > 1:
        assert "key" not in database.file.find_one()
    assert normalized(database)["video"] == CASES[0]["before"]["video"]
    assert not objects.calls


def test_repeated_finish_updates_finish_time_and_does_not_change_video_status(database):
    case = next(row for row in CASES if row["operation"] == "finish" and row["scenario"] == "success")
    seed(database, case)
    times = iter([NOW, NOW.replace(day=16)])
    store = MongoLocalCovers(lambda: database, lambda: NOW)
    objects = Objects()
    covers = LocalCovers(store, objects, Ids(), lambda: next(times), UTC, BASE)
    for day in (15, 16):
        covers.finish("fixture-uploader", "fixture-cover")
        assert database.cover.find_one()["finishTime"].day == day
        assert normalized(database) == case["after"]
    assert not objects.calls


def test_repeated_create_uses_real_identifiers_and_retains_both_registrations(database):
    seed(database, CASES[0])
    objects = Objects()
    application = build_backend_application(
        lambda: database, Urls(), profile="prod", local_clock=lambda: NOW, display_timezone=UTC,
        worker=1, datacenter=1, upload_objects=objects, local_cover_access_base_url=BASE,
    )
    with TestClient(create_user_asgi(application)) as client:
        results = [client.post("/cover/local/createCover", json={"videoId": "fixture-video"},
                               headers={"token": "fixture-local-token"}).json() for _ in range(2)]
    assert all(result["code"] == 0 for result in results)
    assert results[0]["data"]["coverId"] != results[1]["data"]["coverId"]
    assert database.cover.count_documents({}) == database.file.count_documents({}) == 2
    assert normalized(database)["video"] == CASES[0]["before"]["video"]
    assert len(objects.calls) == 2


@pytest.mark.parametrize("failure", [2, 3])
def test_finish_partial_failure_keeps_ready_cover_and_original_video(database, failure):
    case = next(row for row in CASES if row["operation"] == "finish" and row["scenario"] == "success")
    seed(database, case)
    inner = MongoLocalCovers(lambda: database, lambda: NOW)
    calls = []

    class FailingStore:
        def __getattr__(self, name):
            original = getattr(inner, name)
            if not name.startswith("save_"):
                return original

            def save(document):
                calls.append(name)
                if len(calls) == failure:
                    raise RuntimeError("synthetic completion failure")
                original(document)
            return save

    covers = LocalCovers(FailingStore(), Objects(), Ids(), lambda: NOW, UTC, BASE)
    with pytest.raises(RuntimeError, match="synthetic completion failure"):
        covers.finish("fixture-uploader", "fixture-cover")
    assert calls == ["save_cover", "save_file", "save_video"][:failure]
    assert database.cover.find_one()["status"] == "READY"
    assert database.file.find_one()["fileStatus"] == ("READY" if failure == 3 else "CREATED")
    assert normalized(database)["video"] == case["before"]["video"]
