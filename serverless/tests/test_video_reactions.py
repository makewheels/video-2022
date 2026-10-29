"""Complete real Java HTTP/BSON fixtures, opt-in routing and non-replayed failure boundaries."""

import copy
import json
from dataclasses import replace
from datetime import UTC, datetime, timedelta
from pathlib import Path

import pytest
from bson import ObjectId
from pymongo.errors import DuplicateKeyError
from starlette.testclient import TestClient
from test_user_mongo_reads import isolated_database as isolated_database
from test_watch_info import Urls

from video_serverless.adapters.mongo_video_reactions import MongoVideoReactions
from video_serverless.adapters.user_asgi import create_user_asgi
from video_serverless.backend_bootstrap import build_backend_application
from video_serverless.business.video_reactions import VideoReactions

CASES = json.loads((Path(__file__).parents[1] / "contracts" / "video-reactions" / "cases.json")
                   .read_text(encoding="utf-8"))
NOW = datetime(2024, 1, 15, tzinfo=UTC)
HEADERS = {"token": "fixture-reaction-token"}


@pytest.fixture
def database(isolated_database):
    for name in ("user", "video", "video_like", "requestLog", "exceptionLog"):
        isolated_database[name].delete_many({})
    isolated_database.video_like.create_index([("videoId", 1), ("userId", 1)], unique=True, name="video_user_idx")
    isolated_database.user.insert_one({"_id": "fixture-viewer", "token": HEADERS["token"], "phone": "00000000000"})
    return isolated_database


def restore(value, field=None):
    if isinstance(value, dict):
        if set(value) == {"$oid"}:
            return ObjectId(value["$oid"])
        return {name: restore(item, name) for name, item in value.items()}
    if isinstance(value, list):
        return [restore(item) for item in value]
    if field in {"createTime", "updateTime"} and value is not None:
        return datetime(1970, 1, 1, tzinfo=UTC) + timedelta(milliseconds=value)
    return value


def seed(database, case):
    for name, documents in restore(case["before"]).items():
        if documents:
            database[name].insert_many(documents)


def state(database):
    return {name: list(database[name].find().sort("_id", 1)) for name in ("video", "video_like")}


def normalize(value, ids):
    if isinstance(value, datetime):
        utc = value.replace(tzinfo=UTC) if value.tzinfo is None else value.astimezone(UTC)
        delta = utc - datetime(1970, 1, 1, tzinfo=UTC)
        milliseconds = delta.days * 86400000 + delta.seconds * 1000 + delta.microseconds // 1000
        return milliseconds if milliseconds < 10000 else "<server-date>"
    if isinstance(value, ObjectId):
        return ids.get(str(value), {"$oid": str(value)})
    if isinstance(value, list):
        return [normalize(item, ids) for item in value]
    if isinstance(value, dict):
        return {name: normalize(item, ids) for name, item in value.items()}
    return value


def backend(provider, enabled=True):
    options = {"video_reactions": True} if enabled else {}
    application = build_backend_application(provider, Urls(), profile="prod", local_clock=lambda: NOW,
                                             display_timezone=UTC, worker=1, datacenter=1, **options)
    if enabled:
        reactions = replace(application.video_reactions.reactions, clock=lambda: NOW)
        application = replace(application, video_reactions=replace(application.video_reactions, reactions=reactions))
    return application


@pytest.mark.parametrize("case", CASES, ids=lambda row: row["operation"] + ":" + row["scenario"])
def test_old_http_full_bson_and_logs_match_without_changing_production_routes(database, case):
    seed(database, case)
    assert normalize(state(database), {}) == case["before"]
    with TestClient(create_user_asgi(backend(lambda: database))) as client:
        response = client.get("/videoLike/" + case["operation"], params=case["parameters"], headers=case["headers"])
    ids = {str(row["_id"]): "<generated-reaction>" for row in database.video_like.find()
           if isinstance(row["_id"], ObjectId)}
    assert response.status_code == case["status"]
    assert response.headers["Content-Type"] == case["contentType"]
    assert response.json() == case["response"]
    assert normalize(state(database), ids) == case["after"]
    assert database.requestLog.count_documents({}) == case["requestLogs"]
    assert database.exceptionLog.count_documents({}) == case["exceptionLogs"]


def test_default_assembly_has_no_reaction_routes_or_database_access():
    application = backend(lambda: pytest.fail("Disabled routes must not access the database"), enabled=False)
    with TestClient(create_user_asgi(application)) as client:
        for operation in ("like", "dislike", "getStatus"):
            assert client.get("/videoLike/" + operation).status_code == 404
            assert client.options("/videoLike/" + operation).status_code == 404


def test_head_mutations_public_options_and_method_errors_match_old_mvc(database):
    seed(database, CASES[0])
    application = backend(lambda: database)
    assert application.video_reactions.cycle is application.users.cycle
    with TestClient(create_user_asgi(application)) as client:
        for operation in ("like", "dislike"):
            assert client.get("/videoLike/" + operation).status_code == 401
        for _ in range(2):
            assert client.head("/videoLike/like?videoId=fixture-video", headers=HEADERS).status_code == 200
        assert database.video.find_one()["likeCount"] == 3
        assert database.video_like.count_documents({}) == 0
        assert client.head("/videoLike/getStatus?videoId=fixture-video").status_code == 200
        assert client.options("/videoLike/like").status_code == 401
        for operation, headers in (("like", HEADERS), ("getStatus", {})):
            response = client.options("/videoLike/" + operation, headers=headers)
            assert response.status_code == 200 and response.headers["Allow"] == "GET,HEAD,OPTIONS"
        for operation in ("like", "getStatus"):
            response = client.post("/videoLike/" + operation)
            assert response.status_code == 200 and response.json()["code"] == 1
    assert database.requestLog.count_documents({}) == 5
    assert database.requestLog.count_documents({"request.method": "HEAD"}) == 3
    assert database.exceptionLog.count_documents({}) == 2


def test_real_toggle_sequence_and_two_users_keep_independent_actions(database):
    seed(database, CASES[0])
    database.user.insert_one({"_id": "fixture-second-viewer", "token": "fixture-second-token"})
    with TestClient(create_user_asgi(backend(lambda: database))) as client:
        for operation in ("like", "dislike", "dislike"):
            assert client.get("/videoLike/" + operation + "?videoId=fixture-video", headers=HEADERS).json()["code"] == 0
        second = {"token": "fixture-second-token"}
        assert client.get("/videoLike/like?videoId=fixture-video", headers=second).json()["code"] == 0
        for headers, action in ((HEADERS, None), (second, "LIKE"), ({}, None)):
            response = client.get("/videoLike/getStatus?videoId=fixture-video", headers=headers)
            assert response.json()["data"] == {"likeCount": 4, "dislikeCount": 2, "userAction": action}
    row = database.video_like.find_one()
    assert isinstance(row["_id"], ObjectId) and row["userId"] == "fixture-second-viewer"
    assert "_class" not in row


@pytest.mark.parametrize("cancel", [False, True])
def test_counter_failure_keeps_prior_insert_or_remove_and_never_replays(database, cancel):
    seed(database, next(row for row in CASES if row["operation"] == "like"
                        and row["scenario"] == ("same" if cancel else "new")))
    calls = []

    class FailingStore(MongoVideoReactions):
        def increment_count(self, video_id, reaction_type, delta):
            calls.append((reaction_type, delta))
            raise RuntimeError("synthetic reaction counter failure")

    with pytest.raises(RuntimeError, match="synthetic reaction counter failure"):
        VideoReactions(FailingStore(lambda: database), lambda: NOW).react("fixture-viewer", "fixture-video", "LIKE")
    assert calls == [("LIKE", -1 if cancel else 1)]
    assert database.video_like.count_documents({}) == (0 if cancel else 1)
    assert database.video.find_one()["likeCount"] == 3


def test_unique_index_race_does_not_increment_or_implicitly_retry(database):
    seed(database, next(row for row in CASES if row["operation"] == "like" and row["scenario"] == "same"))
    before = copy.deepcopy(state(database))
    calls = []

    class RacedStore(MongoVideoReactions):
        def find_reaction(self, video_id, user_id):
            calls.append("find")
            return None

    with pytest.raises(DuplicateKeyError):
        VideoReactions(RacedStore(lambda: database), lambda: NOW).react("fixture-viewer", "fixture-video", "LIKE")
    assert calls == ["find"]
    assert state(database) == before
