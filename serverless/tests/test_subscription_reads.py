"""Actual old subscription HTTP contracts and complete read-only business BSON."""

import json
from dataclasses import replace
from datetime import UTC, datetime
from pathlib import Path

import pytest
from starlette.testclient import TestClient
from test_user_mongo_reads import isolated_database as isolated_database
from test_video_reactions import normalize, restore
from test_watch_info import Urls

from video_serverless.adapters.mongo_subscription_reads import MongoSubscriptionReads
from video_serverless.adapters.user_asgi import create_user_asgi
from video_serverless.backend_bootstrap import build_backend_application
from video_serverless.business.subscription_reads import Subscriptions

CONTRACT = json.loads((Path(__file__).parents[1] / "contracts" / "subscription-read" / "cases.json")
                      .read_text(encoding="utf-8"))
HEADERS = {"token": "fixture-subscription-token"}


@pytest.fixture
def database(isolated_database):
    for name in ("user", "subscription", "requestLog", "exceptionLog"):
        isolated_database[name].delete_many({})
    for name, documents in restore(CONTRACT["before"]).items():
        isolated_database[name].insert_many(documents)
    return isolated_database


def state(database):
    return {name: list(database[name].find().sort("_id", 1)) for name in ("user", "subscription")}


def backend(provider, enabled=True):
    options = {"subscription_reads": True} if enabled else {}
    return build_backend_application(provider, Urls(), profile="prod", local_clock=lambda: datetime(2024, 1, 15),
                                     display_timezone=UTC, worker=1, datacenter=1, **options)


@pytest.mark.parametrize("case", CONTRACT["cases"], ids=lambda row: row["operation"] + ":" + row["scenario"])
def test_old_http_binding_identity_pagination_and_complete_bson(database, case):
    assert normalize(state(database), {}) == CONTRACT["before"]
    with TestClient(create_user_asgi(backend(lambda: database))) as client:
        response = client.get("/subscription/" + case["operation"], params=case["parameters"], headers=case["headers"])
    assert response.status_code == case["status"]
    assert response.headers["Content-Type"] == case["contentType"]
    assert response.json() == case["response"]
    assert normalize(state(database), {}) == CONTRACT["after"]
    assert database.requestLog.count_documents({}) == case["requestLogs"]
    assert database.exceptionLog.count_documents({}) == case["exceptionLogs"]


def test_default_factory_has_no_subscription_routes_or_database_access():
    app = backend(lambda: pytest.fail("Disabled routes must not access the database"), enabled=False)
    with TestClient(create_user_asgi(app)) as client:
        for operation in ("getStatus", "getMySubscriptions", "subscribe", "unsubscribe"):
            assert client.get("/subscription/" + operation).status_code == 404
            assert client.options("/subscription/" + operation).status_code == 404


def test_head_options_methods_and_write_routes_remain_unchanged(database):
    app = backend(lambda: database)
    assert app.subscriptions.cycle is app.users.cycle
    with TestClient(create_user_asgi(app)) as client:
        for operation in ("getStatus", "getMySubscriptions"):
            path = "/subscription/" + operation
            assert client.get(path).status_code == 401
            assert client.head(path, params={"channelUserId": "channel-0"}, headers=HEADERS).status_code == 200
            assert client.options(path).status_code == 401
            allowed = client.options(path, headers=HEADERS)
            assert allowed.status_code == 200 and allowed.headers["Allow"] == "GET,HEAD,OPTIONS"
            unsupported = client.post(path)
            assert unsupported.status_code == 200 and unsupported.json()["code"] == 1
        for operation in ("subscribe", "unsubscribe"):
            assert client.get("/subscription/" + operation, headers=HEADERS).status_code == 404
    assert database.requestLog.count_documents({}) == 4
    assert database.exceptionLog.count_documents({}) == 2
    assert normalize(state(database), {}) == CONTRACT["before"]


def test_storage_failure_is_logged_instead_of_reporting_unsubscribed(database):
    class FailingStore(MongoSubscriptionReads):
        def subscribed(self, user_id, channel_user_id):
            raise RuntimeError("synthetic subscription read failure")

    app = backend(lambda: database)
    app = replace(app, subscriptions=replace(app.subscriptions, reads=Subscriptions(FailingStore(lambda: database))))
    with TestClient(create_user_asgi(app)) as client:
        response = client.get("/subscription/getStatus?channelUserId=channel-0", headers=HEADERS)
    assert response.json()["code"] == 1 and response.json()["data"] is None
    assert database.exceptionLog.count_documents({}) == database.requestLog.count_documents({}) == 1
    assert normalize(state(database), {}) == CONTRACT["before"]
