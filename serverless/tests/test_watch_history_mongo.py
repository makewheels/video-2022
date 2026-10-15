"""History behavior on actual task-owned Mongo; only signing is a substitute."""

from datetime import UTC, datetime, timedelta

import pytest
from bson import ObjectId
from test_user_mongo_reads import isolated_database  # noqa: F401

from video_serverless.adapters.mongo_watch_history import MongoHistoryCoverUrls, MongoHistoryStore
from video_serverless.business.watch_history import WatchHistory

NOW = datetime(2024, 1, 2, 3, 4, 5, tzinfo=UTC)


@pytest.fixture
def database(isolated_database):  # noqa: F811
    for name in ("watchLog", "video", "cover"):
        isolated_database[name].delete_many({})
    return isolated_database


class Urls:
    def __init__(self):
        self.calls = []

    def sign(self, key, seconds):
        self.calls.append((key, seconds))
        return "https://example.invalid/fixture-cover"


def use_case(database, urls=None):
    return WatchHistory(MongoHistoryStore(lambda: database),
                        MongoHistoryCoverUrls(lambda: database, urls if urls is not None else Urls()))


def row(id, viewer, video, seconds):
    document = {"_id": id, "videoId": video, "createTime": NOW + timedelta(seconds=seconds)}
    if viewer is not None:
        document["viewerId"] = viewer
    return document


def test_duplicate_rows_missing_video_and_private_title_match_old_http(database):
    database.watchLog.insert_many([row("a", "viewer", "video", 3), row("b", "viewer", "video", 2),
                                   row("c", "viewer", "deleted", 1), row("d", "other", "video", 4)])
    database.video.insert_one({"_id": "video", "title": "fixture-private-title",
                               "visibility": "PRIVATE", "coverId": "cover"})
    database.cover.insert_one({"_id": "cover", "key": "fixture/cover.jpg"})
    urls = Urls()
    result = use_case(database, urls).get("viewer")
    assert (result.total, result.page, result.page_size) == (3, 0, 20)
    assert [item.video_id for item in result.items] == ["video", "video", "deleted"]
    assert result.items[0].title == "fixture-private-title"
    assert result.items[0].watched_at == NOW + timedelta(seconds=3)
    assert result.items[0].cover_url == "https://example.invalid/fixture-cover"
    assert result.items[2].title is None and result.items[2].cover_url is None
    assert urls.calls == [("fixture/cover.jpg", 7200)] * 2


def test_anonymous_null_matches_missing_and_clear_preserves_named_viewer(database):
    explicit_null = row("null", None, "two", 2)
    explicit_null["viewerId"] = None
    database.watchLog.insert_many([row("missing", None, "one", 1), explicit_null,
                                   row("named", "viewer", "three", 3)])
    history = use_case(database)
    assert history.get(None).total == 2
    assert [item.video_id for item in history.get(None).items] == ["two", "one"]
    history.clear(None)
    assert [doc["_id"] for doc in database.watchLog.find()] == ["named"]


def test_named_clear_preserves_other_and_anonymous(database):
    database.watchLog.insert_many([row("mine", "viewer", "one", 1), row("other", "other", "two", 2),
                                   row("anonymous", None, "three", 3)])
    use_case(database).clear("viewer")
    assert {doc["_id"] for doc in database.watchLog.find()} == {"other", "anonymous"}


@pytest.mark.parametrize("page,size,expected", [(0, 0, 3), (0, -1, 3), (-1, 2, 2), (2**31 - 1, 2, 2), (1, 2, 1)])
def test_pagination_zero_negative_and_overflow_match_old_http(database, page, size, expected):
    database.watchLog.insert_many([row(str(i), None, "v-" + str(i), i) for i in range(3)])
    result = use_case(database).get(None, page, size)
    assert len(result.items) == expected
    assert (result.total, result.page, result.page_size) == (3, page, size)
    assert result.items[0].video_id == ("v-0" if page == 1 else "v-2")


def test_empty_or_missing_cover_key_does_not_invent_fallback_url(database):
    database.watchLog.insert_many([row("a", "viewer", "one", 1), row("b", "viewer", "two", 2)])
    database.video.insert_many([{"_id": "one", "coverId": "missing"}, {"_id": "two", "coverId": "cover"}])
    database.cover.insert_one({"_id": "cover", "key": ""})
    urls = Urls()
    result = use_case(database, urls).get("viewer")
    assert result.items[0].cover_url == "https://example.invalid/fixture-cover"
    assert result.items[1].cover_url is None
    assert urls.calls == [("", 7200)]


def test_hex_video_and_cover_ids_use_object_id_without_string_fallback(database):
    id = "64b000000000000000000001"
    database.watchLog.insert_one(row("log", "viewer", id, 1))
    database.video.insert_one({"_id": id, "title": "must-not-match"})
    assert use_case(database).get("viewer").items[0].title is None
    database.video.insert_one({"_id": ObjectId(id), "title": "matched", "coverId": id})
    database.cover.insert_one({"_id": id, "key": "must-not-match"})
    assert use_case(database).get("viewer").items[0].cover_url is None
    database.cover.insert_one({"_id": ObjectId(id), "key": "matched-key"})
    urls = Urls()
    assert use_case(database, urls).get("viewer").items[0].title == "matched"
    assert urls.calls == [("matched-key", 7200)]


def test_clear_failure_after_actual_delete_is_not_replayed(database):
    database.watchLog.insert_one(row("mine", "viewer", "video", 1))
    actual = MongoHistoryStore(lambda: database)

    class Interrupted:
        calls = 0

        def clear(self, viewer):
            self.calls += 1
            actual.clear(viewer)
            raise RuntimeError("synthetic acknowledgement failure")

    store = Interrupted()
    with pytest.raises(RuntimeError, match="synthetic acknowledgement failure"):
        WatchHistory(store, Urls()).clear("viewer")
    assert store.calls == 1
    assert database.watchLog.count_documents({}) == 0


def test_signing_failure_has_no_history_writes_or_retries(database):
    database.watchLog.insert_one(row("log", "viewer", "video", 1))
    database.video.insert_one({"_id": "video", "coverId": "cover"})
    database.cover.insert_one({"_id": "cover", "key": "fixture/key"})
    original = database.watchLog.find_one()

    class Unavailable:
        calls = 0

        def sign(self, *args):
            self.calls += 1
            raise RuntimeError("synthetic signing failure")

    urls = Unavailable()
    with pytest.raises(RuntimeError, match="synthetic signing failure"):
        use_case(database, urls).get("viewer")
    assert urls.calls == 1
    assert database.watchLog.find_one() == original
