"""Real generated ObjectIds, complete old HTTP/BSON and mutation failure boundaries."""

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

from video_serverless.adapters.mongo_comment_writes import MongoCommentWrites
from video_serverless.adapters.user_asgi import create_user_asgi
from video_serverless.backend_bootstrap import build_backend_application
from video_serverless.business.comment_writes import CommentWrites
from video_serverless.ports.users import UserSnapshot

CASES = json.loads((Path(__file__).parents[1] / "contracts/comment-write/cases.json").read_text(encoding="utf-8"))
NOW = datetime(2024, 1, 15, tzinfo=UTC)
USER = UserSnapshot("fixture-viewer", phone="00000000000", nickname="fixture viewer",
                    avatar_url="https://example.invalid/avatar")


@pytest.fixture
def database(isolated_database):
    for name in ("user", "video", "comment", "comment_like", "requestLog", "exceptionLog"):
        isolated_database[name].delete_many({})
    isolated_database.comment_like.create_index([("commentId", 1), ("userId", 1)], unique=True, name="comment_user_idx")
    isolated_database.user.insert_one({"_id": USER.id, "token": "fixture-comment-token", "phone": USER.phone,
                                     "nickname": USER.nickname, "avatarUrl": USER.avatar_url})
    return isolated_database


def seed(database, case):
    for name, documents in case["before"].items():
        for document in copy.deepcopy(documents):
            for field in ("createTime", "updateTime"):
                if document.get(field) is not None:
                    document[field] = datetime(1970, 1, 1, tzinfo=UTC) + timedelta(milliseconds=document[field])
            database[name].insert_one(document)


def state(database):
    return {name: list(database[name].find().sort("_id", 1)) for name in ("video", "comment", "comment_like")}


def normalize(value, ids, field=None):
    if isinstance(value, datetime):
        utc = value.replace(tzinfo=UTC) if value.tzinfo is None else value.astimezone(UTC)
        delta = utc - datetime(1970, 1, 1, tzinfo=UTC)
        milliseconds = delta.days * 86400000 + delta.seconds * 1000 + delta.microseconds // 1000
        return milliseconds if milliseconds < 10000 else "<server-date>"
    if isinstance(value, ObjectId):
        return ids.get(str(value), str(value))
    if isinstance(value, str):
        if field in {"createTime", "updateTime"} and not value.startswith("1970-"):
            return "<server-date>"
        return ids.get(value, value)
    if isinstance(value, list):
        return [normalize(item, ids) for item in value]
    if isinstance(value, dict):
        return {name: normalize(item, ids, name) for name, item in value.items()}
    return value


def identifiers(database):
    return {str(row["_id"]): "<generated-" + name + ">" for name in ("comment", "comment_like")
            for row in database[name].find() if isinstance(row["_id"], ObjectId)}


def backend(database, enabled=True):
    application = build_backend_application(lambda: database, Urls(), profile="prod", local_clock=lambda: NOW,
                                             display_timezone=UTC, worker=1, datacenter=1,
                                             public_comment_reads=True, comment_writes=enabled)
    if enabled:
        writes = replace(application.comment_writes.writes, clock=lambda: NOW)
        application = replace(application, comment_writes=replace(application.comment_writes, writes=writes))
    return application


@pytest.mark.parametrize("case", CASES, ids=lambda row: row["operation"] + ":" + row["scenario"])
def test_old_write_http_complete_documents_generated_ids_and_logs(database, case):
    seed(database, case)
    headers = {} if case["scenario"] == "no-token" else {
        "token": "invalid" if case["scenario"] == "bad-token" else "fixture-comment-token"}
    with TestClient(create_user_asgi(backend(database))) as client:
        if case["operation"] == "add":
            response = client.post("/comment/add", content=case["input"].encode(),
                                   headers=headers | {"Content-Type": "application/json"})
        else:
            parameters = {} if case["scenario"] == "missing-parameter" else {"commentId": case["commentId"]}
            response = client.get("/comment/" + case["operation"], params=parameters, headers=headers)
    ids = identifiers(database)
    assert response.status_code == case["status"]
    assert response.headers["Content-Type"] == case["contentType"]
    assert normalize(response.json(), ids) == case["response"]
    assert normalize(state(database), ids) == case["after"]
    assert database.requestLog.count_documents({}) == case["requestLogs"]
    assert database.exceptionLog.count_documents({}) == case["exceptionLogs"]


def test_comment_write_routes_are_explicit_and_share_existing_cycle(database):
    application = backend(database)
    assert application.comment_writes.cycle is application.users.cycle
    with TestClient(create_user_asgi(backend(database, enabled=False))) as client:
        assert client.post("/comment/add", json={}).status_code == 404
        assert client.get("/comment/delete?commentId=x").status_code == 404
        assert client.get("/comment/like?commentId=x").status_code == 404


def test_reply_parent_counter_survives_insert_failure_without_replay(database):
    seed(database, CASES[0])
    calls = []

    class FailingStore(MongoCommentWrites):
        def create_comment(self, document):
            calls.append("insert")
            raise RuntimeError("synthetic comment insert failure")

    writes = CommentWrites(FailingStore(lambda: database), lambda: NOW)
    with pytest.raises(RuntimeError, match="synthetic comment insert failure"):
        writes.add(USER, "fixture-video", "fixture", "fixture-parent")
    assert calls == ["insert"]
    assert database.comment.find_one({"_id": "fixture-parent"})["replyCount"] == 2
    assert database.comment.count_documents({}) == 2
    assert database.video.find_one()["commentCount"] == 2


def test_video_counter_failure_keeps_inserted_comment_without_replay(database):
    seed(database, CASES[0])
    calls = []

    class FailingStore(MongoCommentWrites):
        def increment_video_comments(self, video_id, delta):
            calls.append(delta)
            raise RuntimeError("synthetic video counter failure")

    with pytest.raises(RuntimeError, match="synthetic video counter failure"):
        CommentWrites(FailingStore(lambda: database), lambda: NOW).add(USER, "fixture-video", "fixture", None)
    assert calls == [1]
    assert database.comment.count_documents({}) == 3
    assert len(identifiers(database)) == 1
    assert database.video.find_one()["commentCount"] == 2


def test_real_unique_like_index_failure_does_not_increment_or_retry(database):
    seed(database, next(row for row in CASES if row["scenario"] == "toggle-off"))

    class RacedStore(MongoCommentWrites):
        def find_like(self, comment_id, user_id):
            return None

    with pytest.raises(DuplicateKeyError):
        CommentWrites(RacedStore(lambda: database), lambda: NOW).like(USER.id, "fixture-parent")
    assert database.comment_like.count_documents({}) == 2
    assert database.comment.find_one({"_id": "fixture-parent"})["likeCount"] == 3


def test_real_ids_reply_flattening_toggle_and_cascade_work_with_public_reads(database):
    seed(database, CASES[0])
    headers = {"token": "fixture-comment-token"}
    with TestClient(create_user_asgi(backend(database))) as client:
        def add(content, parent=None):
            response = client.post("/comment/add", json={"videoId": "fixture-video", "content": content,
                                                        "parentId": parent}, headers=headers)
            assert response.json()["code"] == 0
            row = response.json()["data"]
            assert ObjectId.is_valid(row["id"])
            return row

        parent = add("fixture new root")
        child = add("fixture new child", parent["id"].upper())
        grandchild = add("fixture nested reply", child["id"])
        assert child["parentId"] == grandchild["parentId"] == parent["id"]
        assert database.comment.find_one({"_id": ObjectId(parent["id"])})["replyCount"] == 2
        for _ in range(2):
            assert client.get("/comment/like", params={"commentId": parent["id"]}, headers=headers).json()["code"] == 0
        assert database.comment.find_one({"_id": ObjectId(parent["id"])})["likeCount"] == 0
        assert client.get("/comment/getCount?videoId=fixture-video").json()["data"]["count"] == 5
        assert client.get("/comment/delete", params={"commentId": parent["id"]}, headers=headers).json()["code"] == 0
        assert database.comment.count_documents({}) == database.video.find_one()["commentCount"] == 2
        assert client.get("/comment/getCount?videoId=fixture-video").json()["data"]["count"] == 2


def test_delete_counter_failure_keeps_prior_cascade_removal(database):
    seed(database, CASES[0])
    calls = []

    class FailingStore(MongoCommentWrites):
        def increment_video_comments(self, video_id, delta):
            calls.append(delta)
            raise RuntimeError("synthetic delete counter failure")

    writes = CommentWrites(FailingStore(lambda: database), lambda: NOW)
    with pytest.raises(RuntimeError, match="synthetic delete counter failure"):
        writes.delete(USER.id, "fixture-parent")
    assert calls == [-2]
    assert database.comment.count_documents({}) == 0
    assert database.video.find_one()["commentCount"] == 2
    assert writes.delete(USER.id, "fixture-parent").error_message == "评论不存在"
    assert calls == [-2]


def test_head_mutation_options_protection_and_unsupported_methods_match_old_mvc(database):
    seed(database, CASES[0])
    headers = {"token": "fixture-comment-token"}
    with TestClient(create_user_asgi(backend(database))) as client:
        for _ in range(2):
            assert client.head("/comment/like?commentId=fixture-parent", headers=headers).status_code == 200
        assert database.comment.find_one({"_id": "fixture-parent"})["likeCount"] == 3
        assert database.comment_like.count_documents({}) == 0
        assert client.options("/comment/like").status_code == 401
        allow = client.options("/comment/like", headers=headers)
        assert allow.status_code == 200 and allow.headers["Allow"] == "GET,HEAD,OPTIONS"
        unsupported = client.post("/comment/like")
        assert unsupported.status_code == 200 and unsupported.json()["code"] == 1
        assert client.get("/comment/add").json()["code"] == 1
        assert client.head("/comment/delete?commentId=fixture-parent", headers=headers).status_code == 200
    assert database.comment.count_documents({}) == 0
    assert database.video.find_one()["commentCount"] == 0
    assert database.requestLog.count_documents({}) == 4
    assert database.requestLog.count_documents({"request.method": "HEAD"}) == 3
    assert database.exceptionLog.count_documents({}) == 2
