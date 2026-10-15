"""Actual standalone Mongo comparison with the Java heartbeat/progress HTTP contract."""

from dataclasses import replace
from datetime import UTC, datetime, timedelta

import pytest
from bson import ObjectId
from bson.int64 import Int64
from test_user_mongo_reads import isolated_database  # noqa: F401

from video_serverless.adapters.mongo_progress import MongoProgressStore
from video_serverless.business.progress import ProgressUpdates
from video_serverless.ports.progress import ProgressHeartbeat, ProgressRecord

NOW = datetime(2024, 1, 2, 3, 4, 5, tzinfo=UTC)


@pytest.fixture
def database(isolated_database):  # noqa: F811
    isolated_database.progress.delete_many({})
    return isolated_database


def use_case(database, now=NOW):
    return ProgressUpdates(MongoProgressStore(lambda: database, lambda: now), lambda: now)


def heartbeat(**changes):
    return replace(ProgressHeartbeat("unknown", None, "client", "session", 42, "playing"), **changes)


@pytest.mark.parametrize("status", ["PLAYING", "paused", "PAUSED", "", " playing", None])
def test_other_statuses_do_not_read_provider_or_clock(status):
    def forbidden():
        raise AssertionError("No provider or clock access outside lowercase playing")

    ProgressUpdates(MongoProgressStore(forbidden), forbidden).update(heartbeat(player_status=status))


@pytest.mark.parametrize("viewer", [None, "", "fixture-viewer"])
def test_java_bson_shape_and_identity_selection(database, viewer):
    updates = use_case(database)
    updates.update(heartbeat(viewer_id=viewer))
    original = database.progress.find_one()
    assert isinstance(original["_id"], ObjectId)
    assert isinstance(original["progressInMillis"], Int64)
    assert "_class" not in original
    assert original["createTime"] == NOW.replace(tzinfo=None)
    assert original["updateTime"] == NOW.replace(tzinfo=None)
    assert original.get("viewerId") == viewer
    assert ("viewerId" in original) == (viewer is not None)
    updates.update(heartbeat(viewer_id=viewer, client_id="other", session_id="other", player_time=-7))
    assert database.progress.count_documents({}) == (2 if viewer is None else 1)
    saved = database.progress.find_one({"_id": original["_id"]})
    if viewer is not None:
        assert saved["clientId"] == "client"
        assert saved["sessionId"] == "other"
        assert saved["progressInMillis"] == -7


def test_replacement_preserves_creation_drops_unknown_and_omits_null_fields(database):
    use_case(database).update(heartbeat())
    original = database.progress.find_one()
    database.progress.update_one({"_id": original["_id"]}, {"$set": {"fixtureExtra": "drop-me"}})
    use_case(database, NOW + timedelta(seconds=1)).update(heartbeat(session_id=None, player_time=None))
    saved = database.progress.find_one()
    assert saved["_id"] == original["_id"]
    assert saved["createTime"] == original["createTime"]
    assert saved["updateTime"] == (NOW + timedelta(seconds=1)).replace(tzinfo=None)
    assert all(key not in saved for key in ("fixtureExtra", "sessionId", "progressInMillis"))
    record = MongoProgressStore(lambda: database).find("unknown", None, "client")
    assert record.milliseconds is None
    assert record.created_at == NOW


def test_missing_identity_fails_without_a_progress_write_and_empty_client_is_valid(database):
    with pytest.raises(ValueError, match="viewerId和clientId不能同时为空"):
        use_case(database).update(heartbeat(client_id=None))
    assert database.progress.count_documents({}) == 0
    use_case(database).update(heartbeat(client_id=""))
    assert database.progress.find_one()["clientId"] == ""


def test_read_missing_progress_and_video_is_not_validated(database):
    store = MongoProgressStore(lambda: database)
    assert store.find("unknown", None, "client") is None
    use_case(database).update(heartbeat(video_id=None))
    assert "videoId" not in database.progress.find_one()
    assert store.find(None, None, "client").milliseconds == 42


@pytest.mark.parametrize("missing", [True, False])
def test_constructor_date_defaults_differ_from_explicit_null(database, missing):
    document = {"videoId": "unknown", "clientId": "client", "progressInMillis": Int64(3)}
    if not missing:
        document.update(createTime=None, updateTime=None)
    database.progress.insert_one(document)
    record = MongoProgressStore(lambda: database, lambda: NOW).find("unknown", None, "client")
    assert record.created_at == (NOW if missing else None)
    assert record.updated_at == (NOW if missing else None)


def test_save_failure_after_real_write_is_not_retried(database):
    actual = MongoProgressStore(lambda: database)

    class Interrupted:
        calls = 0

        def find(self, *args):
            return actual.find(*args)

        def save(self, record):
            self.calls += 1
            actual.save(record)
            raise RuntimeError("synthetic acknowledgement failure")

    interrupted = Interrupted()
    with pytest.raises(RuntimeError, match="synthetic acknowledgement failure"):
        ProgressUpdates(interrupted, lambda: NOW).update(heartbeat())
    assert interrupted.calls == 1
    assert database.progress.count_documents({}) == 1
    assert database.progress.find_one()["progressInMillis"] == 42


def test_provider_read_failure_never_attempts_save():
    class Unavailable:
        calls = 0

        def find(self, *args):
            raise RuntimeError("synthetic read failure")

        def save(self, record):
            self.calls += 1

    store = Unavailable()
    with pytest.raises(RuntimeError, match="synthetic read failure"):
        ProgressUpdates(store, lambda: NOW).update(heartbeat())
    assert store.calls == 0


def test_recreate_removed_id_keeps_legacy_save_upsert_semantics(database):
    store = MongoProgressStore(lambda: database)
    record = ProgressRecord("fixture-deleted", "v", "viewer", "c", "s", 9, NOW, NOW)
    store.save(record)
    assert database.progress.find_one()["_id"] == "fixture-deleted"
    database.progress.delete_many({})
    store.save(record)
    assert database.progress.count_documents({}) == 1
