from concurrent.futures import ThreadPoolExecutor
from datetime import UTC, datetime

import pytest
from bson import ObjectId
from test_user_mongo_reads import isolated_database as isolated_database

from video_serverless.adapters.mongo_watching_ids import MongoWatchingIds
from video_serverless.business.watching_ids import WatchingIds

NOW = datetime(2026, 1, 1, tzinfo=UTC)


@pytest.fixture
def allocation(isolated_database):
    for name in ("client", "session"):
        isolated_database[name].delete_many({})
    return isolated_database, WatchingIds(MongoWatchingIds(lambda: isolated_database), lambda: NOW)


@pytest.mark.parametrize("kind", ["client", "session"])
def test_each_allocation_persists_separate_legacy_objectid_and_metadata(allocation, kind):
    database, ids = allocation
    allocate = getattr(ids, kind)
    first = allocate("127.0.0.1", "fixture-agent")
    second = allocate("::1", None)
    assert first != second
    assert ObjectId.is_valid(first) and ObjectId.is_valid(second)
    assert database[kind].count_documents({}) == 2
    assert database[kind].find_one({"_id": ObjectId(first)}) == {
        "_id": ObjectId(first), "createTime": NOW.replace(tzinfo=None),
        "ip": "127.0.0.1", "userAgent": "fixture-agent",
    }
    assert "userAgent" not in database[kind].find_one({"_id": ObjectId(second)})


def test_parallel_instances_allocate_distinct_persistent_ids(allocation):
    database, _ = allocation
    instances = [WatchingIds(MongoWatchingIds(lambda: database), lambda: NOW) for _ in range(4)]
    with ThreadPoolExecutor(max_workers=8) as pool:
        ids = list(pool.map(lambda index: instances[index % 4].client("127.0.0.1", "fixture"), range(40)))
    assert len(set(ids)) == 40
    assert database["client"].count_documents({}) == 40
    assert database["session"].count_documents({}) == 0


@pytest.mark.parametrize("kind", ["client", "session"])
def test_storage_failure_never_returns_an_unpersisted_identity(kind):
    class Unavailable:
        def insert_one(self, document):
            raise RuntimeError("fixture write failure")

        def get_collection(self, name):
            return self

    ids = WatchingIds(MongoWatchingIds(Unavailable), lambda: NOW)
    with pytest.raises(RuntimeError, match="fixture write failure"):
        getattr(ids, kind)("127.0.0.1", None)
