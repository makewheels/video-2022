"""Actual public comment HTTP/Mongo comparison; no mutation or external services."""

import copy
import json
from datetime import UTC, datetime, timedelta
from pathlib import Path

import pytest
from bson import ObjectId
from starlette.testclient import TestClient
from test_user_mongo_reads import isolated_database as isolated_database
from test_watch_info import Urls

from video_serverless.adapters.mongo_comment_reads import MongoCommentReads
from video_serverless.adapters.user_asgi import create_user_asgi
from video_serverless.backend_bootstrap import build_backend_application
from video_serverless.business.comment_reads import Comments

CASES = json.loads((Path(__file__).parents[1] / "contracts/comment-read/cases.json").read_text(encoding="utf-8"))


@pytest.fixture
def database(isolated_database):
    for name in ("video", "comment", "user", "requestLog", "exceptionLog", "comment_like"):
        isolated_database[name].delete_many({})
    return isolated_database


def seed(database, case):
    if case["video"] is not None:
        database.video.insert_one(copy.deepcopy(case["video"]))
    for comment in copy.deepcopy(case["comments"]):
        for name in ("createTime", "updateTime"):
            if comment.get(name) is not None:
                comment[name] = datetime(1970, 1, 1, tzinfo=UTC) + timedelta(milliseconds=comment[name])
        database.comment.insert_one(comment)


def backend(database, enabled=True):
    return build_backend_application(lambda: database, Urls(), profile="prod", local_clock=datetime.now,
                                     display_timezone=UTC, worker=1, datacenter=1, public_comment_reads=enabled)


@pytest.mark.parametrize("case", CASES, ids=lambda row: row["operation"] + ":" + row["scenario"])
def test_full_old_response_logs_and_business_read_only_effects(database, case):
    seed(database, case)
    before_comments = list(database.comment.find().sort("_id", 1))
    before_video = database.video.find_one()
    headers = {"token": "fixture-invalid-token"} if case["scenario"] == "invalid-token" else {}
    with TestClient(create_user_asgi(backend(database))) as client:
        response = client.get("/comment/" + case["operation"], params=case["parameters"], headers=headers)
    assert response.status_code == case["status"]
    assert response.headers["Content-Type"] == case["contentType"]
    assert response.json() == case["response"]
    assert list(database.comment.find().sort("_id", 1)) == before_comments
    assert database.video.find_one() == before_video
    assert database.comment_like.count_documents({}) == 0
    assert database.requestLog.count_documents({}) == case["requestLogs"]
    assert database.exceptionLog.count_documents({}) == case["exceptionLogs"]


def test_explicit_public_routes_share_one_cycle_and_are_disabled_by_default(database):
    application = backend(database)
    assert application.comments.cycle is application.users.cycle
    with TestClient(create_user_asgi(backend(database, enabled=False))) as client:
        assert client.get("/comment/getCount?videoId=fixture-video").status_code == 404
    assert database.requestLog.count_documents({}) == database.exceptionLog.count_documents({}) == 0


def test_count_happens_before_page_and_failure_does_not_continue_or_retry():
    calls = []

    class Reads:
        def video_exists(self, video_id):
            calls.append("exists")
            return True

        def count_top(self, video_id):
            calls.append("count")
            raise RuntimeError("synthetic comment count failure")

        def top(self, *args):
            calls.append("page")
            raise AssertionError("Must not read after failed count")

    with pytest.raises(RuntimeError, match="synthetic comment count failure"):
        Comments(Reads()).page("fixture-video", 0, 20, "createTime")
    assert calls == ["exists", "count"]


def test_object_id_entities_and_string_video_references_use_legacy_filter(database):
    video_id = ObjectId("507f1f77bcf86cd799439011")
    comment_id = ObjectId("507f1f77bcf86cd799439012")
    database.video.insert_one({"_id": video_id, "visibility": "PRIVATE"})
    database.comment.insert_one({"_id": comment_id, "videoId": str(video_id), "content": "fixture"})
    comments = Comments(MongoCommentReads(lambda: database))
    page = comments.page(str(video_id), 0, 20, "createTime")
    assert page.total == 1 and page.content[0]["id"] == str(comment_id)
    assert comments.count(str(video_id)) == 1
    assert database.comment.find_one()["_id"] == comment_id
