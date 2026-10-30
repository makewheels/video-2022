"""Replay actual legacy MVC results and complete BSON using the isolated backend factory."""

import json
import struct
import time
from datetime import UTC, datetime
from pathlib import Path

import pytest
from bson import json_util
from starlette.testclient import TestClient
from test_user_mongo_reads import isolated_database as isolated_database

from video_serverless.adapters.user_asgi import create_user_asgi
from video_serverless.backend_bootstrap import build_backend_application

FIXED = Path(__file__).resolve().parents[1] / "contracts" / "catalog-matrix" / "catalog.json"
CASES = json.loads(FIXED.read_text(encoding="utf-8"))
COLLECTIONS = ("video", "user", "cover")
PUBLISH_TIME = 1704164644000


class NoMedia:
    def sign(self, key, seconds):
        pytest.fail("Catalog matrix has no cover objects; external media access is forbidden")


@pytest.fixture(scope="module")
def composed(isolated_database):
    database = isolated_database
    application = build_backend_application(
        lambda: database, NoMedia(), profile="prod", local_clock=lambda: datetime.now(UTC),
        display_timezone=UTC, worker=1, datacenter=1,
    )
    with TestClient(create_user_asgi(application)) as client:
        yield database, client


def snapshot(database):
    return {
        name: json.loads(json_util.dumps(
            list(database[name].find().sort("_id", 1)), json_options=json_util.CANONICAL_JSON_OPTIONS,
        ))
        for name in COLLECTIONS
    }


def bson_values(value):
    if isinstance(value, dict):
        if set(value) == {"$numberDouble"}:
            return {"$numberDouble": struct.pack(">d", float(value["$numberDouble"])).hex()}
        return {key: bson_values(item) for key, item in value.items()}
    if isinstance(value, list):
        return [bson_values(item) for item in value]
    return value


def normalize_dates(body, start, end):
    data = body.get("data")
    if not isinstance(data, dict):
        return
    for row in data.get("list", data.get("content", [])):
        if row.get("createTime") is not None:
            date = datetime.fromisoformat(row["createTime"])
            epoch = int(date.timestamp() * 1000)
            assert row["createTimeString"] == date.astimezone(UTC).strftime("%Y-%m-%d %H:%M:%S")
            if start <= epoch <= end:
                row["createTime"] = "<constructor-date>"
                row["createTimeString"] = "<local:constructor-date>"
            else:
                row["createTimeString"] = f"<local:{epoch}>"
        if row.get("youtubePublishTimeString") is not None:
            expected = datetime.fromtimestamp(PUBLISH_TIME / 1000, UTC).strftime("%Y-%m-%d %H:%M:%S")
            assert row["youtubePublishTimeString"] == expected
            row["youtubePublishTimeString"] = f"<local:{PUBLISH_TIME}>"


@pytest.mark.parametrize("case", CASES, ids=lambda case: case["id"])
def test_remaining_catalog_http_and_complete_bson_match_old_java(composed, case):
    database, client = composed
    for name in (*COLLECTIONS, "requestLog", "exceptionLog"):
        database[name].delete_many({})
    for name in COLLECTIONS:
        documents = json_util.loads(json.dumps(case["before"][name]))
        if documents:
            database[name].insert_many(documents)
    assert bson_values(snapshot(database)) == bson_values(case["before"])
    start = time.time_ns() // 1_000_000
    response = client.get(case["path"], params=case["parameters"])
    end = time.time_ns() // 1_000_000
    body = response.json()
    normalize_dates(body, start, end)
    assert response.status_code == case["status"]
    assert response.headers["content-type"] == case["contentType"]
    assert body == case["body"]
    assert bson_values(snapshot(database)) == bson_values(case["after"]) == bson_values(case["before"])
    assert database.requestLog.count_documents({}) == case["requestLogs"]
    assert database.exceptionLog.count_documents({}) == case["exceptionLogs"]
