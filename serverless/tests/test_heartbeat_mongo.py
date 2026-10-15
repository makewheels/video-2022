"""Legacy HTTP/BSON heartbeats against a task-owned Mongo database."""

from dataclasses import replace
from datetime import UTC, datetime
from decimal import Decimal

import pytest
from bson import ObjectId
from bson.decimal128 import Decimal128
from bson.int64 import Int64
from test_user_mongo_reads import isolated_database  # noqa: F401

from video_serverless.adapters.mongo_heartbeat import MongoHeartbeatStore
from video_serverless.adapters.mongo_progress import MongoProgressStore
from video_serverless.business.heartbeat import Heartbeats
from video_serverless.business.progress import ProgressUpdates
from video_serverless.ports.heartbeat import Heartbeat
from video_serverless.ports.users import UserSnapshot

NOW = datetime(2024, 1, 2, 3, 4, 5, tzinfo=UTC)


@pytest.fixture
def database(isolated_database):  # noqa: F811
    isolated_database.heartbeat.delete_many({})
    isolated_database.progress.delete_many({})
    return isolated_database


def use_case(database, store=None, progress=None):
    return Heartbeats(store if store is not None else MongoHeartbeatStore(lambda: database),
                      progress if progress is not None else ProgressUpdates(
                          MongoProgressStore(lambda: database), lambda: NOW), lambda: NOW)


def heartbeat(**changes):
    return replace(Heartbeat(video_id="unknown", client_id="client", session_id="session",
                             player_time=42, player_status="playing"), **changes)


def test_full_bson_mapping_matches_actual_java_http(database):
    original = heartbeat(player_volume=Decimal("0.1250"), client_time=NOW, created_at=NOW.replace(year=2000),
                         video_status="READY", player_provider="fixture-player", type="TIMER", event="fixture-event")
    use_case(database).add(original)
    saved = database.heartbeat.find_one()
    assert isinstance(saved.pop("_id"), ObjectId)
    assert saved == {"videoId": "unknown", "clientId": "client", "sessionId": "session",
                     "playerTime": Int64(42), "playerStatus": "playing", "playerVolume": Decimal128("0.1250"),
                     "clientTime": NOW.replace(tzinfo=None), "createTime": NOW.replace(tzinfo=None),
                     "videoStatus": "READY", "playerProvider": "fixture-player",
                     "type": "TIMER", "event": "fixture-event"}
    assert isinstance(saved["playerTime"], Int64)
    assert original.created_at.year == 2000
    assert original.viewer_id is None
    assert database.progress.find_one()["progressInMillis"] == 42


@pytest.mark.parametrize("status", ["PLAYING", "paused", "", None])
def test_nonplaying_still_saves_heartbeat_but_never_updates_progress(database, status):
    use_case(database).add(heartbeat(player_status=status, client_id=None))
    assert database.heartbeat.count_documents({}) == 1
    assert database.progress.count_documents({}) == 0


def test_missing_identity_failure_keeps_the_earlier_heartbeat(database):
    with pytest.raises(ValueError, match="viewerId和clientId不能同时为空"):
        use_case(database).add(heartbeat(client_id=None))
    assert database.heartbeat.count_documents({}) == 1
    assert database.progress.count_documents({}) == 0


def test_authenticated_override_then_anonymous_body_viewer_and_no_residual_identity(database):
    service = use_case(database)
    body = heartbeat(viewer_id="body-viewer")
    service.add(body, UserSnapshot("actual-viewer"))
    service.add(body)
    service.add(heartbeat(client_id="fresh-client"))
    assert [doc.get("viewerId") for doc in database.heartbeat.find()] == ["actual-viewer", "body-viewer", None]
    assert body.viewer_id == "body-viewer"
    assert database.progress.count_documents({}) == 3


@pytest.mark.parametrize("id", ["fixture-heartbeat", "", "64b000000000000000000001"])
def test_supplied_id_replaces_entity_and_hex_does_not_fall_back_to_string(database, id):
    store = MongoHeartbeatStore(lambda: database)
    if ObjectId.is_valid(id):
        database.heartbeat.insert_one({"_id": id, "fixture": "original"})
    store.save(heartbeat(id=id, event="drop-me", player_volume=Decimal("0.5")))
    store.save(Heartbeat(id=id, client_id="other", created_at=NOW))
    key = ObjectId(id) if ObjectId.is_valid(id) else id
    assert database.heartbeat.find_one({"_id": key}) == {
        "_id": key, "clientId": "other", "createTime": NOW.replace(tzinfo=None)}
    assert database.heartbeat.count_documents({}) == (2 if ObjectId.is_valid(id) else 1)
    if ObjectId.is_valid(id):
        assert database.heartbeat.find_one({"_id": id})["fixture"] == "original"


def test_store_failure_after_actual_write_does_not_retry_or_run_progress(database):
    actual = MongoHeartbeatStore(lambda: database)

    class Interrupted:
        calls = 0

        def save(self, value):
            self.calls += 1
            actual.save(value)
            raise RuntimeError("synthetic acknowledgement failure")

    store = Interrupted()
    with pytest.raises(RuntimeError, match="synthetic acknowledgement failure"):
        use_case(database, store=store).add(heartbeat())
    assert store.calls == 1
    assert database.heartbeat.count_documents({}) == 1
    assert database.progress.count_documents({}) == 0


def test_progress_failure_keeps_heartbeat_and_never_replays_save(database):
    class Unavailable:
        calls = 0

        def update(self, value):
            self.calls += 1
            raise RuntimeError("synthetic progress failure")

    progress = Unavailable()
    with pytest.raises(RuntimeError, match="synthetic progress failure"):
        use_case(database, progress=progress).add(heartbeat())
    assert progress.calls == 1
    assert database.heartbeat.count_documents({}) == 1


def test_clock_failure_does_not_access_either_store():
    def unavailable():
        raise RuntimeError("synthetic clock failure")

    class Forbidden:
        def save(self, value):
            pytest.fail("must not save")

        def update(self, value):
            pytest.fail("must not update progress")

    with pytest.raises(RuntimeError, match="synthetic clock failure"):
        Heartbeats(Forbidden(), Forbidden(), unavailable).add(heartbeat())
