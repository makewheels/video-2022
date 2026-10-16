from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone

import pytest
from bson import Int64
from test_user_mongo_reads import isolated_database as isolated_database

from video_serverless.adapters.mongo_identifiers import MongoIdSequences
from video_serverless.business.identifiers import SNOWFLAKE_EPOCH, Identifiers, snowflake_string

NOW = 2208988800000


@pytest.fixture
def database(isolated_database):
    isolated_database.id_counters.delete_many({})
    return isolated_database


def ids(database, now=NOW):
    return Identifiers(MongoIdSequences(lambda: database, 1, 1), lambda: datetime(2023, 1, 1, 0, 0, 8),
                       lambda: now, lambda: "007", 1, 1)


def test_snowflake_layout_matches_actual_locked_hutool_5_8_25_probe():
    # JDK21 + hutool-all5.8.25, fixed lastTimestamp, explicit node1:1 and sequence0.
    assert snowflake_string(NOW, 1, 1, 1) == "3859404870251581441"
    assert snowflake_string(NOW, 1, 1, 2) == "3859404870251581442"


def test_long_id_uses_existing_counter_format_and_expands_serial_width(database):
    generator = ids(database)
    assert generator.user_id() == "u_200001007"
    assert generator.long_id("f_") == "f_200002007"
    collection = database.id_counters
    assert collection.find_one({"_id": "longId:2"})["counter"] == 2
    collection.update_one({"_id": "longId:2"}, {"$set": {"counter": Int64(99999)}})
    assert generator.user_id() == "u_2100000007"


def test_local_wall_time_is_not_silently_converted_to_utc(database):
    generator = Identifiers(MongoIdSequences(lambda: database, 1, 1),
                            lambda: datetime(2023, 1, 1, 8, tzinfo=timezone(timedelta(hours=8))),
                            lambda: NOW, lambda: "007", 1, 1)
    assert generator.user_id() == "u_720000001007"


def test_parallel_instances_and_restart_share_token_and_long_id_sequences(database):
    instances = [ids(database) for _ in range(8)]
    with ThreadPoolExecutor(max_workers=8) as pool:
        tokens = list(pool.map(lambda index: instances[index % 8].token(), range(80)))
        users = list(pool.map(lambda index: instances[index % 8].user_id(), range(40)))
    assert len(set(tokens)) == 80
    assert len(set(users)) == 40
    assert sorted(int(token) & 4095 for token in tokens) == list(range(80))
    assert ids(database).token() not in tokens
    assert ids(database).user_id() not in users
    state = database.id_counters.find_one({"_id": "tokenSnowflake:1:1"})
    assert set(state) == {"_id", "counter"}
    assert isinstance(state["counter"], Int64)


def test_sequence_overflow_and_small_clock_rollback_do_not_repeat_tokens(database):
    floor = (NOW - SNOWFLAKE_EPOCH) << 12
    database.id_counters.insert_one({"_id": "tokenSnowflake:1:1", "counter": Int64(floor + 4095)})
    token = ids(database).token()
    assert token == snowflake_string(NOW + 1, 1, 1, 0)
    assert ids(database, NOW - 100).token() == snowflake_string(NOW + 1, 1, 1, 1)
    with pytest.raises(RuntimeError, match="Clock moved backwards"):
        ids(database, NOW - 2000).token()


def test_reservation_failure_does_not_fall_back_to_process_local_tokens():
    class Unavailable:
        def reserve_token_slot(self, timestamp_ms):
            raise RuntimeError("fixture storage failure")

    generator = Identifiers(Unavailable(), lambda: datetime(2023, 1, 1), lambda: NOW, lambda: "007", 1, 1)
    with pytest.raises(RuntimeError, match="fixture storage failure"):
        generator.token()


def test_short_id_base36_expanded_sequence_and_local_date_counter(database):
    generator = ids(database)
    first = generator.short_id()
    # 2023-01-01 is day 365 from the Java epoch, concatenated with serial 01 and random 007.
    assert int(first, 36) == 36501007
    assert first == first.upper()
    assert database.id_counters.find_one({"_id": "shortId:2023-01-01"})["counter"] == 1
    database.id_counters.update_one({"_id": "shortId:2023-01-01"}, {"$set": {"counter": Int64(99)}})
    assert int(generator.short_id(), 36) == 365100007


def test_parallel_short_ids_use_shared_reservation_and_next_day_resets_counter(database):
    database.id_counters.delete_many({})
    with ThreadPoolExecutor(max_workers=8) as pool:
        values = list(pool.map(lambda _: ids(database).short_id(), range(40)))
    assert len(set(values)) == 40
    assert {int(value, 36) for value in values} == {int(f"365{serial:02d}007") for serial in range(1, 41)}
    next_day = Identifiers(MongoIdSequences(lambda: database, 1, 1), lambda: datetime(2023, 1, 2),
                           lambda: NOW, lambda: "007", 1, 1)
    assert int(next_day.short_id(), 36) == 36601007
