"""Real legacy subscription mutations, complete BSON, opt-in routing and failure boundaries."""

import copy
import json
from dataclasses import replace
from datetime import UTC, datetime
from pathlib import Path

import pytest
from bson import Int64, ObjectId
from pymongo.errors import DuplicateKeyError
from starlette.testclient import TestClient
from test_user_mongo_reads import isolated_database as isolated_database
from test_video_reactions import normalize, restore
from test_watch_info import Urls

from video_serverless.adapters.mongo_subscription_writes import MongoSubscriptionWrites
from video_serverless.adapters.user_asgi import create_user_asgi
from video_serverless.backend_bootstrap import build_backend_application
from video_serverless.business.subscription_writes import SubscriptionWrites

CASES = json.loads((Path(__file__).parents[1] / "contracts/interactions/subscription-write.json")
                   .read_text(encoding="utf-8"))
NOW = datetime(2024, 1, 15, tzinfo=UTC)
HEADERS = {"token": "fixture-interaction-token"}


@pytest.fixture
def database(isolated_database):
    for name in ("user", "subscription", "requestLog", "exceptionLog"):
        isolated_database.drop_collection(name)
    isolated_database.subscription.create_index(
        [("userId", 1), ("channelUserId", 1)], unique=True, name="uk_user_channel")
    return isolated_database


def seed(database, case):
    for name, documents in restore(case["before"]).items():
        if documents:
            database[name].insert_many(documents)


def state(database):
    return {name: list(database[name].find().sort("_id", 1)) for name in ("user", "subscription")}


def backend(provider, enabled=True):
    application = build_backend_application(provider, Urls(), profile="prod", local_clock=lambda: NOW,
                                             display_timezone=UTC, worker=1, datacenter=1,
                                             subscription_writes=enabled, subscription_reads=enabled)
    if enabled:
        writes = replace(application.subscription_writes.writes, clock=lambda: NOW)
        application = replace(application, subscription_writes=replace(application.subscription_writes, writes=writes))
    return application


@pytest.mark.parametrize("case", CASES, ids=lambda row: row["operation"] + ":" + row["scenario"])
def test_real_legacy_http_full_bson_errors_and_partial_writes(database, case):
    seed(database, case)
    before_ids = {row["_id"] for row in database.subscription.find()}
    assert normalize(state(database), {}) == case["before"]
    with TestClient(create_user_asgi(backend(lambda: database))) as client:
        response = client.get("/subscription/" + case["operation"], params=case["parameters"], headers=case["headers"])
    ids = {str(row["_id"]): "<generated-subscription>" for row in database.subscription.find()
           if isinstance(row["_id"], ObjectId) and row["_id"] not in before_ids}
    assert response.status_code == case["status"]
    assert response.headers["Content-Type"] == case["contentType"]
    assert response.json() == case["response"]
    assert normalize(state(database), ids) == case["after"]
    assert database.requestLog.count_documents({}) == case["requestLogs"]
    assert database.exceptionLog.count_documents({}) == case["exceptionLogs"]
    if case["scenario"] == "counter-overflow" and case["operation"] == "subscribe":
        assert isinstance(database.user.find_one({"_id": "fixture-channel"})["subscriberCount"], Int64)


def test_disabled_subscription_mutations_do_not_access_the_database():
    app = backend(lambda: pytest.fail("Disabled routes must not access the database"), enabled=False)
    with TestClient(create_user_asgi(app)) as client:
        for operation in ("subscribe", "unsubscribe"):
            assert client.get("/subscription/" + operation).status_code == 404
            assert client.options("/subscription/" + operation).status_code == 404


@pytest.mark.parametrize("operation", ["subscribe", "unsubscribe"])
def test_head_mutations_options_and_unsupported_methods_keep_legacy_order(database, operation):
    seed(database, next(row for row in CASES if row["operation"] == operation
                        and row["scenario"] == ("new" if operation == "subscribe" else "existing")))
    app = backend(lambda: database)
    assert app.subscription_writes.cycle is app.users.cycle
    with TestClient(create_user_asgi(app)) as client:
        path = "/subscription/" + operation
        assert client.get(path).status_code == 401
        assert client.options(path).status_code == 401
        allowed = client.options(path, headers=HEADERS)
        assert allowed.status_code == 200 and allowed.headers["Allow"] == "GET,HEAD,OPTIONS"
        assert client.head(path, params={"channelUserId": "fixture-channel"}, headers=HEADERS).status_code == 200
        unsupported = client.put(path)
        assert unsupported.status_code == 200 and unsupported.json()["code"] == 1
    assert database.subscription.count_documents({}) == (1 if operation == "subscribe" else 0)
    assert database.requestLog.count_documents({}) == 2
    assert database.exceptionLog.count_documents({}) == 1


def test_subscribe_reads_and_unsubscribe_share_the_existing_channel_counts(database):
    seed(database, next(row for row in CASES if row["operation"] == "subscribe" and row["scenario"] == "new"))
    with TestClient(create_user_asgi(backend(lambda: database))) as client:
        parameters = {"channelUserId": "fixture-channel"}
        assert client.get("/subscription/subscribe", params=parameters, headers=HEADERS).json()["code"] == 0
        assert client.get("/subscription/getStatus", params=parameters, headers=HEADERS).json()["data"] is True
        assert client.get("/subscription/getMySubscriptions", headers=HEADERS).json()["data"] == ["fixture-channel"]
        row = database.subscription.find_one()
        assert isinstance(row["_id"], ObjectId) and "_class" not in row
        assert database.user.find_one({"_id": "fixture-channel"})["subscriberCount"] == 6
        assert client.get("/subscription/unsubscribe", params=parameters, headers=HEADERS).json()["code"] == 0
        assert client.get("/subscription/getStatus", params=parameters, headers=HEADERS).json()["data"] is False
        assert client.get("/subscription/unsubscribe", params=parameters, headers=HEADERS).json()["message"] == "未订阅"
    assert database.user.find_one({"_id": "fixture-channel"})["subscriberCount"] == 5


def test_real_unique_index_race_does_not_increment_or_retry(database):
    seed(database, next(row for row in CASES if row["operation"] == "subscribe" and row["scenario"] == "existing"))
    before = copy.deepcopy(state(database))
    calls = []

    class RacedStore(MongoSubscriptionWrites):
        def find_subscription(self, user_id, channel_user_id):
            calls.append("find")
            return None

    writes = SubscriptionWrites(RacedStore(lambda: database), lambda: NOW)
    with pytest.raises(DuplicateKeyError):
        writes.subscribe("fixture-viewer", "fixture-channel")
    assert calls == ["find"] and state(database) == before


@pytest.mark.parametrize("operation", ["subscribe", "unsubscribe"])
def test_counter_failure_keeps_prior_mutation_and_is_not_replayed(database, operation):
    seed(database, next(row for row in CASES if row["operation"] == operation
                        and row["scenario"] == ("new" if operation == "subscribe" else "existing")))
    calls = []

    class FailingStore(MongoSubscriptionWrites):
        def increment_subscribers(self, channel_user_id, delta):
            calls.append(delta)
            raise RuntimeError("synthetic subscription counter failure")

    app = backend(lambda: database)
    writes = SubscriptionWrites(FailingStore(lambda: database), lambda: NOW)
    app = replace(app, subscription_writes=replace(app.subscription_writes, writes=writes))
    with TestClient(create_user_asgi(app)) as client:
        response = client.get("/subscription/" + operation, params={"channelUserId": "fixture-channel"},
                              headers=HEADERS)
    assert response.json()["code"] == 1 and calls == ([1] if operation == "subscribe" else [-1])
    assert database.subscription.count_documents({}) == (1 if operation == "subscribe" else 0)
    assert database.user.find_one({"_id": "fixture-channel"})["subscriberCount"] == 5
    assert database.requestLog.count_documents({}) == database.exceptionLog.count_documents({}) == 1
