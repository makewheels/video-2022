"""Real old notification HTTP/BSON contracts and explicit read/write assembly boundaries."""

import json
from dataclasses import replace
from datetime import UTC, datetime
from pathlib import Path

import pytest
from starlette.testclient import TestClient
from test_user_mongo_reads import isolated_database as isolated_database
from test_video_reactions import normalize, restore
from test_watch_info import Urls

from video_serverless.adapters.mongo_notifications import MongoNotifications
from video_serverless.adapters.user_asgi import create_user_asgi
from video_serverless.backend_bootstrap import build_backend_application
from video_serverless.business.notifications import Notifications

ROOT = Path(__file__).parents[1] / "contracts/interactions"
CASES = [case for name in ("notification-read", "notification-write")
         for case in json.loads((ROOT / (name + ".json")).read_text(encoding="utf-8"))]
HEADERS = {"token": "fixture-interaction-token"}


@pytest.fixture
def database(isolated_database):
    for name in ("user", "notification", "requestLog", "exceptionLog"):
        isolated_database.drop_collection(name)
    return isolated_database


def seed(database, case):
    for name, documents in restore(case["before"]).items():
        if documents:
            database[name].insert_many(documents)
    if case["scenario"] == "write-failure":
        database.command({"collMod": "notification", "validator": {"read": False}})


def state(database):
    return {name: list(database[name].find().sort("_id", 1)) for name in ("user", "notification")}


def backend(provider, *, reads=True, writes=True):
    return build_backend_application(provider, Urls(), profile="prod", local_clock=lambda: datetime(2024, 1, 15),
                                     display_timezone=UTC, worker=1, datacenter=1,
                                     notification_reads=reads, notification_writes=writes)


@pytest.mark.parametrize("case", CASES, ids=lambda row: row["operation"] + ":" + row["scenario"])
def test_old_http_full_bson_paging_binding_owner_and_partial_failures(database, case):
    seed(database, case)
    assert normalize(state(database), {}) == case["before"]
    with TestClient(create_user_asgi(backend(lambda: database))) as client:
        response = client.request(case["method"], "/notification/" + case["operation"],
                                  params=case["parameters"], content=case["input"], headers=case["headers"])
    assert response.status_code == case["status"]
    assert response.headers["Content-Type"] == case["contentType"]
    assert response.json() == case["response"]
    assert normalize(state(database), {}) == case["after"]
    assert database.requestLog.count_documents({}) == case["requestLogs"]
    assert database.exceptionLog.count_documents({}) == case["exceptionLogs"]


def test_default_notification_assembly_has_no_routes_or_database_access():
    app = backend(lambda: pytest.fail("Disabled routes must not access the database"), reads=False, writes=False)
    with TestClient(create_user_asgi(app)) as client:
        for operation in ("getMyNotifications", "getUnreadCount", "markAsRead", "markAllAsRead"):
            assert client.get("/notification/" + operation).status_code == 404
            assert client.post("/notification/" + operation, json={}).status_code == 404
            assert client.options("/notification/" + operation).status_code == 404


@pytest.mark.parametrize("reads", [False, True])
def test_notification_read_and_write_capabilities_are_independently_opt_in(database, reads):
    seed(database, CASES[0])
    app = backend(lambda: database, reads=reads, writes=not reads)
    assert app.notifications.cycle is app.users.cycle
    with TestClient(create_user_asgi(app)) as client:
        assert client.get("/notification/getUnreadCount", headers=HEADERS).status_code == (200 if reads else 404)
        assert client.post("/notification/markAllAsRead", headers=HEADERS).status_code == (404 if reads else 200)


@pytest.mark.parametrize("operation", ["getMyNotifications", "getUnreadCount", "markAsRead", "markAllAsRead"])
def test_methods_authentication_and_options_follow_real_old_mvc(database, operation):
    seed(database, CASES[0])
    app = backend(lambda: database)
    write = operation.startswith("mark")
    with TestClient(create_user_asgi(app)) as client:
        path = "/notification/" + operation
        assert client.request("POST" if write else "GET", path, json={}).status_code == 200
        assert client.options(path).status_code == 200
        allowed = client.options(path, headers=HEADERS)
        assert allowed.status_code == 200
        assert allowed.headers["Allow"] == ("POST,OPTIONS" if write else "GET,HEAD,OPTIONS")
        unsupported = client.put(path)
        assert unsupported.status_code == 200 and unsupported.json()["code"] == 1
        if not write:
            assert client.head(path, headers=HEADERS).status_code == 200
    assert database.requestLog.count_documents({}) == (3 if write else 4)
    assert database.exceptionLog.count_documents({}) == 1


def test_mark_read_roundtrip_keeps_other_users_unknown_fields_and_null_missing_flags(database):
    seed(database, next(row for row in CASES if row["scenario"] == "mixed-flags"))
    with TestClient(create_user_asgi(backend(lambda: database))) as client:
        before = client.get("/notification/getUnreadCount", headers=HEADERS).json()["data"]
        assert client.post("/notification/markAsRead", json={"notificationId": "fixture-unread"},
                           headers=HEADERS).json()["code"] == 0
        assert client.get("/notification/getUnreadCount", headers=HEADERS).json()["data"] == before - 1
        for _ in range(2):
            assert client.post("/notification/markAllAsRead", headers=HEADERS).json()["code"] == 0
        assert client.get("/notification/getUnreadCount", headers=HEADERS).json()["data"] == 0
    assert database.notification.find_one({"_id": "fixture-other"})["read"] is False
    assert database.notification.find_one({"_id": "fixture-unread"})["untouched"] == "keep"
    assert "read" not in database.notification.find_one({"_id": "fixture-missing"})
    assert database.notification.find_one({"_id": "fixture-null"})["read"] is None


def test_notification_page_precedes_count_and_storage_failure_is_logged(database):
    seed(database, CASES[0])
    before = state(database)
    calls = []

    class FailingStore(MongoNotifications):
        def page(self, user_id, skip, limit):
            calls.append("page")
            return super().page(user_id, skip, limit)

        def count(self, user_id):
            calls.append("count")
            raise RuntimeError("synthetic notification count failure")

    app = backend(lambda: database)
    reads = Notifications(FailingStore(lambda: database))
    app = replace(app, notifications=replace(app.notifications, notifications=reads))
    with TestClient(create_user_asgi(app)) as client:
        response = client.get("/notification/getMyNotifications", headers=HEADERS)
    assert response.json()["code"] == 1 and response.json()["data"] is None
    assert calls == ["page", "count"] and state(database) == before
    assert database.requestLog.count_documents({}) == database.exceptionLog.count_documents({}) == 1
