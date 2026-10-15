"""Session lifecycle on real isolated Mongo, compared with legacy HTTP/BSON assertions."""

from dataclasses import replace
from datetime import UTC, datetime, timedelta

import pytest
from bson import ObjectId
from bson.int64 import Int64
from test_user_mongo_reads import isolated_database  # noqa: F401

from video_serverless.adapters.mongo_playback_sessions import MongoPlaybackSessionStore
from video_serverless.business.playback_sessions import PlaybackSessions
from video_serverless.ports.playback_sessions import PlaybackUpdate, StartPlayback

NOW = datetime(2024, 1, 2, 3, 4, 5, tzinfo=UTC)


@pytest.fixture
def database(isolated_database):  # noqa: F811
    isolated_database.playbackSession.delete_many({})
    return isolated_database


def service(database, now=NOW):
    return PlaybackSessions(MongoPlaybackSessionStore(lambda: database), lambda: now)


def test_start_default_bson_and_each_call_creates_a_new_session(database):
    use_case = service(database)
    first = use_case.start(StartPlayback())
    second = use_case.start(StartPlayback())
    assert first != second
    saved = database.playbackSession.find_one({"_id": ObjectId(first)})
    assert saved == {"_id": ObjectId(first), "startTime": NOW.replace(tzinfo=None),
                     "createTime": NOW.replace(tzinfo=None), "updateTime": NOW.replace(tzinfo=None),
                     "totalPlayDurationMs": Int64(0), "maxProgressMs": Int64(0), "currentProgressMs": Int64(0),
                     "heartbeatCount": 0, "exitType": "PLAYING", "resolution": "auto"}
    for field in ("totalPlayDurationMs", "maxProgressMs", "currentProgressMs"):
        assert isinstance(saved[field], Int64)
    assert type(saved["heartbeatCount"]) is int


def test_authenticated_identity_is_saved_but_anonymous_update_does_not_change_it(database):
    use_case = service(database)
    id = use_case.start(StartPlayback("watch", "video", "client", "session"), "fixture-user")
    use_case.heartbeat(PlaybackUpdate(id, current_time_ms=42))
    saved = MongoPlaybackSessionStore(lambda: database).find(id)
    assert (saved.watch_id, saved.video_id, saved.user_id) == ("watch", "video", "fixture-user")
    assert (saved.client_id, saved.session_id, saved.current_progress_ms) == ("client", "session", 42)


def test_paused_updates_and_backward_seek_preserve_max_but_duration_can_decrease(database):
    use_case = service(database)
    id = use_case.start(StartPlayback())
    use_case.heartbeat(PlaybackUpdate(id, 50, 60, "720p", is_playing=False))
    use_case.heartbeat(PlaybackUpdate(id, -5, -3, ""))
    store = MongoPlaybackSessionStore(lambda: database)
    saved = store.find(id)
    assert (saved.max_progress_ms, saved.current_progress_ms, saved.duration_ms) == (50, -5, -3)
    assert saved.resolution == ""
    use_case.heartbeat(PlaybackUpdate(id))
    saved = store.find(id)
    assert (saved.max_progress_ms, saved.current_progress_ms, saved.duration_ms) == (50, None, -3)
    assert saved.heartbeat_count == 3
    assert "currentProgressMs" not in database.playbackSession.find_one()


def test_exit_and_late_heartbeat_keep_end_time_and_custom_exit_type(database):
    id = service(database).start(StartPlayback())
    service(database).exit(PlaybackUpdate(id, current_time_ms=80, exit_type="fixture-other"))
    exited = MongoPlaybackSessionStore(lambda: database).find(id)
    assert exited.heartbeat_count == 0
    service(database, NOW + timedelta(seconds=1)).heartbeat(PlaybackUpdate(id, current_time_ms=90))
    later = MongoPlaybackSessionStore(lambda: database).find(id)
    assert later.ended_at == exited.ended_at == NOW
    assert later.exit_type == "fixture-other"
    assert later.max_progress_ms == 90
    assert later.heartbeat_count == 1
    assert later.updated_at == NOW + timedelta(seconds=1)
    service(database).exit(PlaybackUpdate(id))
    assert "exitType" not in database.playbackSession.find_one()


@pytest.mark.parametrize("method", ["heartbeat", "exit"])
def test_missing_session_is_success_but_null_id_fails_without_writes(database, method):
    use_case = service(database)
    getattr(use_case, method)(PlaybackUpdate("fixture-missing"))
    with pytest.raises(ValueError, match="ID must not be null"):
        getattr(use_case, method)(PlaybackUpdate())
    assert database.playbackSession.count_documents({}) == 0


def test_absent_counter_fails_without_save_but_exit_still_works(database):
    id = service(database).start(StartPlayback())
    query = {"_id": ObjectId(id)}
    database.playbackSession.update_one(query, {"$unset": {"heartbeatCount": ""}})
    before = database.playbackSession.find_one()
    with pytest.raises(ValueError, match="heartbeat count is absent"):
        service(database).heartbeat(PlaybackUpdate(id, current_time_ms=90))
    assert database.playbackSession.find_one() == before
    service(database).exit(PlaybackUpdate(id, current_time_ms=90))
    assert database.playbackSession.find_one()["currentProgressMs"] == 90


def test_int_counter_overflow_matches_java_wrapping(database):
    id = service(database).start(StartPlayback())
    database.playbackSession.update_one({"_id": ObjectId(id)}, {"$set": {"heartbeatCount": 2**31 - 1}})
    service(database).heartbeat(PlaybackUpdate(id))
    assert database.playbackSession.find_one()["heartbeatCount"] == -(2**31)


def test_null_max_stays_null_and_replacement_drops_unknown_fields(database):
    id = service(database).start(StartPlayback())
    database.playbackSession.update_one({"_id": ObjectId(id)},
                                       {"$unset": {"maxProgressMs": ""}, "$set": {"fixtureExtra": "drop-me"}})
    service(database).heartbeat(PlaybackUpdate(id, current_time_ms=100))
    document = database.playbackSession.find_one()
    assert "maxProgressMs" not in document
    assert "fixtureExtra" not in document
    assert document["currentProgressMs"] == 100


def test_object_id_never_falls_back_to_equal_string_id(database):
    id = "64b000000000000000000001"
    database.playbackSession.insert_one({"_id": id, "fixture": "original"})
    assert MongoPlaybackSessionStore(lambda: database).find(id) is None
    service(database).heartbeat(PlaybackUpdate(id))
    assert database.playbackSession.find_one() == {"_id": id, "fixture": "original"}


@pytest.mark.parametrize("method", ["heartbeat", "exit"])
def test_actual_save_then_acknowledgement_failure_is_not_retried(database, method):
    id = service(database).start(StartPlayback())
    actual = MongoPlaybackSessionStore(lambda: database)

    class Interrupted:
        calls = 0

        def find(self, key):
            return actual.find(key)

        def save(self, snapshot):
            self.calls += 1
            actual.save(snapshot)
            raise RuntimeError("synthetic acknowledgement failure")

    interrupted = Interrupted()
    use_case = PlaybackSessions(interrupted, lambda: NOW)
    with pytest.raises(RuntimeError, match="synthetic acknowledgement failure"):
        getattr(use_case, method)(PlaybackUpdate(id, current_time_ms=7))
    assert interrupted.calls == 1
    assert actual.find(id).current_progress_ms == 7
    assert actual.find(id).heartbeat_count == (1 if method == "heartbeat" else 0)


def test_deleted_session_can_be_recreated_by_inflight_legacy_save(database):
    store = MongoPlaybackSessionStore(lambda: database)
    id = service(database).start(StartPlayback())
    snapshot = store.find(id)
    database.playbackSession.delete_many({})
    assert store.save(replace(snapshot, current_progress_ms=9)) == id
    assert store.find(id).current_progress_ms == 9
