"""Old readiness selection and exact status-only effects against isolated Mongo."""

import copy
import json
from datetime import UTC, datetime
from pathlib import Path

import pytest
from bson import ObjectId
from test_user_mongo_reads import isolated_database as isolated_database

from video_serverless.adapters.mongo_video_readiness import MongoVideoReadiness
from video_serverless.business.video_readiness import VideoReadiness

CASES = json.loads((Path(__file__).parents[1] / "contracts/video-readiness/cases.json").read_text(encoding="utf-8"))
DATE = datetime(1970, 1, 1, 0, 0, 12, 345000, tzinfo=UTC)


@pytest.fixture
def database(isolated_database):
    isolated_database.video.delete_many({})
    isolated_database.transcode.delete_many({})
    return isolated_database


@pytest.mark.parametrize("case", CASES)
def test_old_status_and_exact_mongo_side_effects_match_java(database, case):
    before = copy.deepcopy(case["before"])
    assert before["updateTime"] == 12345
    before["updateTime"] = DATE
    database.video.insert_one(before)
    if case["lanes"]:
        database.transcode.insert_many(copy.deepcopy(case["lanes"]))
    lanes_before = list(database.transcode.find())
    writes = []

    class RecordedStore(MongoVideoReadiness):
        def update_status(self, video_id, status):
            writes.append((video_id, status))
            super().update_status(video_id, status)

    result = VideoReadiness(RecordedStore(lambda: database)).advance(
        before["_id"], tuple(before["transcodeIds"]), before["status"])
    assert result == case["status"]
    after = database.video.find_one()
    assert after["updateTime"] == DATE.replace(tzinfo=None)
    after["updateTime"] = 12345
    assert after == case["after"]
    assert list(database.transcode.find()) == lanes_before
    assert len(writes) == int(before["status"] != case["status"])


def test_object_id_query_deduplicates_ids_and_ignores_missing_references(database):
    identifier = ObjectId("507f1f77bcf86cd799439011")
    database.transcode.insert_one({"_id": identifier, "provider": "LOCAL_TRANSCODE", "status": "FINISHED"})
    database.video.insert_one({"_id": identifier, "status": "TRANSCODING", "keep": {"value": 1}})
    store = MongoVideoReadiness(lambda: database)
    references = (str(identifier), str(identifier), "missing")
    result = VideoReadiness(store).advance(str(identifier), references, "TRANSCODING")
    assert result == "READY"
    assert database.video.find_one() == {"_id": identifier, "status": "READY", "keep": {"value": 1}}
    assert len(store.lanes((str(identifier), str(identifier)))) == 1


def test_failed_status_write_propagates_without_retry_or_other_writes(database):
    database.transcode.insert_one({"_id": "lane", "provider": "LOCAL_TRANSCODE", "status": "FINISHED"})
    database.video.insert_one({"_id": "video", "status": "TRANSCODING"})
    writes = []

    class FailingStore(MongoVideoReadiness):
        def update_status(self, video_id, status):
            writes.append((video_id, status))
            raise RuntimeError("synthetic readiness persistence failure")

    with pytest.raises(RuntimeError, match="synthetic readiness persistence failure"):
        VideoReadiness(FailingStore(lambda: database)).advance("video", ("lane",), "TRANSCODING")
    assert writes == [("video", "READY")]
    assert database.video.find_one()["status"] == "TRANSCODING"


def test_missing_video_is_not_upserted_and_null_id_collection_is_rejected(database):
    database.transcode.insert_one({"_id": "lane", "provider": "LOCAL_TRANSCODE", "status": "FINISHED"})
    store = MongoVideoReadiness(lambda: database)
    assert VideoReadiness(store).advance("absent-video", ("lane",), "TRANSCODING") == "READY"
    assert database.video.count_documents({}) == 0
    with pytest.raises(ValueError):
        store.lanes(None)
