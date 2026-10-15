"""Actual Mongo counter/event writes with a scripted IP port, compared with old MVC contracts."""

from dataclasses import replace
from datetime import UTC, datetime
from decimal import Decimal

import pytest
from bson import ObjectId
from bson.decimal128 import Decimal128
from bson.int64 import Int64
from pymongo.errors import WriteError
from test_user_mongo_reads import isolated_database as isolated_database

from video_serverless.adapters.mongo_watch_log import MongoWatchLogStore
from video_serverless.business.watch_log import WatchLogs
from video_serverless.ports.watch_log import WatchVisit

NOW = datetime(2024, 1, 2, 3, 4, 5, tzinfo=UTC)
VISIT = WatchVisit("fixture-video", "client", "session", None, "127.0.0.2", "fixture-agent")
INFO = {"province": "fixture-province", "city": "fixture-city", "district": "fixture-district", "extra": "preserved"}


@pytest.fixture
def database(isolated_database):
    isolated_database.watchLog.delete_many({})
    isolated_database.video.delete_many({})
    isolated_database.video.insert_one({"_id": "fixture-video", "status": "READY", "title": "fixture-title",
                                       "visibility": "PRIVATE", "watch": {"watchCount": 0}})
    return isolated_database


class Locations:
    def __init__(self, outcomes=None):
        self.calls = []
        self.outcomes = outcomes

    def lookup(self, ip):
        self.calls.append(ip)
        result = INFO if self.outcomes is None else self.outcomes[len(self.calls) - 1]
        if isinstance(result, Exception):
            raise result
        return result


def use_case(database, locations=None, store=None):
    return WatchLogs(MongoWatchLogStore(lambda: database) if store is None else store,
                     Locations() if locations is None else locations, lambda: NOW)


def count(database):
    return database.video.find_one({"_id": "fixture-video"})["watch"]["watchCount"]


def test_private_anonymous_and_cross_viewer_client_share_dedup(database):
    locations = Locations()
    service = use_case(database, locations)
    assert service.add(VISIT, "READY") == 0
    assert service.add(replace(VISIT, viewer_id="other-viewer", client_id="other-client"), "READY") == 0
    assert count(database) == 1 and isinstance(count(database), Int64)
    event = database.watchLog.find_one()
    assert database.watchLog.count_documents({}) == 1
    assert isinstance(event.pop("_id"), ObjectId)
    assert event == {"videoId": "fixture-video", "clientId": "client", "sessionId": "session", "ip": "127.0.0.2",
                     "userAgent": "fixture-agent", "videoStatus": "READY", "createTime": NOW.replace(tzinfo=None),
                     "ipInfo": INFO}
    assert locations.calls == ["127.0.0.2"] * 2


def test_requested_status_drives_counter_but_actual_status_is_saved(database):
    database.video.update_one({"_id": "fixture-video"}, {"$set": {"status": "TRANSCODING"}})
    service = use_case(database)
    assert service.add(VISIT, "READY") == service.add(VISIT, "READY") == 0
    assert count(database) == 2
    assert database.watchLog.count_documents({"videoStatus": "TRANSCODING"}) == 2


def test_first_ip_failure_preserves_count_without_event_and_next_request_can_increment_again(database):
    locations = Locations([RuntimeError("synthetic first IP failure"), INFO, INFO])
    service = use_case(database, locations)
    with pytest.raises(RuntimeError, match="synthetic first IP failure"):
        service.add(VISIT, "READY")
    assert count(database) == 1 and database.watchLog.count_documents({}) == 0
    assert locations.calls == ["127.0.0.2"]
    assert service.add(VISIT, "READY") == 0
    assert count(database) == 2 and database.watchLog.count_documents({}) == 1


def test_second_ip_failure_keeps_event_then_duplicate_succeeds_without_lookup(database):
    locations = Locations([INFO, RuntimeError("synthetic second IP failure")])
    service = use_case(database, locations)
    with pytest.raises(RuntimeError, match="synthetic second IP failure"):
        service.add(VISIT, "READY")
    assert count(database) == 1 and database.watchLog.count_documents({}) == 1
    assert service.add(VISIT, "READY") == 0
    assert count(database) == 1 and len(locations.calls) == 2


def test_null_session_shared_dedup_and_other_status_does_not_increment(database):
    service = use_case(database)
    assert service.add(replace(VISIT, session_id=None), "READY") == 0
    assert service.add(replace(VISIT, session_id=None, client_id="other"), "READY") == 0
    assert count(database) == 1 and database.watchLog.count_documents({}) == 1
    assert service.add(replace(VISIT, session_id="other"), "PAUSED") == 0
    assert count(database) == 1 and database.watchLog.count_documents({}) == 2


def test_video_existence_precedes_duplicate_and_unknown_never_calls_ip(database):
    locations = Locations()
    database.watchLog.insert_one({"videoId": "missing", "sessionId": "session", "videoStatus": "READY"})
    assert use_case(database, locations).add(replace(VISIT, video_id="missing"), "READY") == 22
    assert locations.calls == [] and count(database) == 0


def test_nested_ip_decimal_null_list_long_match_old_bson(database):
    info = {"nullable": None, "decimal": Decimal("1.2500"), "long": 2**31,
            "nested": {"value": 2}, "list": [None, Decimal("1.50")]}
    assert use_case(database, Locations([info, INFO])).add(VISIT, "READY") == 0
    stored = database.watchLog.find_one()["ipInfo"]
    assert stored == {"nullable": None, "decimal": Decimal128("1.2500"), "long": Int64(2**31),
                      "nested": {"value": 2}, "list": [None, Decimal128("1.50")]}
    assert isinstance(stored["long"], Int64)
    assert info["decimal"] == Decimal("1.2500")


def test_dotted_ip_map_fails_after_counter_before_event(database):
    locations = Locations([{"nested": {"fixture.dot": "value"}}])
    with pytest.raises(ValueError, match="IP map key"):
        use_case(database, locations).add(VISIT, "READY")
    assert count(database) == 1 and database.watchLog.count_documents({}) == 0
    assert len(locations.calls) == 1


def test_uppercase_hex_request_uses_canonical_event_id_and_does_not_dedup(database):
    id = "64B0000000000000000000AB"
    database.video.insert_one({"_id": ObjectId(id), "status": "READY", "watch": {"watchCount": 0}})
    service = use_case(database)
    visit = replace(VISIT, video_id=id)
    assert service.add(visit, "READY") == service.add(visit, "READY") == 0
    assert database.watchLog.count_documents({"videoId": id.lower()}) == 2
    assert database.video.find_one({"_id": ObjectId(id)})["watch"]["watchCount"] == 2


def test_null_watch_subdocument_fails_before_ip_or_event(database):
    database.video.update_one({"_id": "fixture-video"}, {"$set": {"watch": None}})
    locations = Locations()
    with pytest.raises(WriteError):
        use_case(database, locations).add(VISIT, "READY")
    assert locations.calls == [] and database.watchLog.count_documents({}) == 0
    assert database.video.find_one()["watch"] is None


def test_save_acknowledgement_failure_does_not_repeat_increment_or_event(database):
    class Interrupted(MongoWatchLogStore):
        calls = 0

        def save(self, event):
            self.calls += 1
            super().save(event)
            raise RuntimeError("synthetic acknowledgement failure")

    store = Interrupted(lambda: database)
    locations = Locations()
    with pytest.raises(RuntimeError, match="synthetic acknowledgement failure"):
        use_case(database, locations, store).add(VISIT, "READY")
    assert count(database) == 1 and database.watchLog.count_documents({}) == 1
    assert store.calls == 1 and len(locations.calls) == 1


def test_first_null_ip_is_omitted_but_second_null_is_error_after_event(database):
    assert use_case(database, Locations([None, INFO])).add(VISIT, "READY") == 0
    assert "ipInfo" not in database.watchLog.find_one()
    database.watchLog.delete_many({})
    with pytest.raises(ValueError, match="cannot dereference null"):
        use_case(database, Locations([INFO, None])).add(VISIT, "READY")
    assert database.watchLog.count_documents({}) == 1 and count(database) == 2
