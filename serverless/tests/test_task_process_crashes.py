"""Actual child-process termination with test-only write ordering and durable Mongo writes."""

import os
import queue
import subprocess
import sys
import threading
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import pytest
from pymongo import MongoClient
from test_user_mongo_reads import isolated_database as isolated_database

WORKER = Path(__file__).parent / "helpers/task_crash_worker.py"
TASK = "fc-PROBE-fixture-crash"


@pytest.fixture
def database(isolated_database):
    for name in ("fcTask", "syntheticWake", "syntheticProvider"):
        isolated_database[name].delete_many({})
    return isolated_database


def crash_at(database, stage):
    with subprocess.Popen([sys.executable, "-B", str(WORKER), os.environ["MONGODB_URI"], database.name, stage],
                          stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE) as child:
        lines = queue.Queue()
        reader = threading.Thread(target=lambda: lines.put(child.stdout.readline()), daemon=True)
        reader.start()
        try:
            assert lines.get(timeout=15) == b"checkpoint\n"
            child.kill()
            child.wait(timeout=10)
            assert child.returncode != 0
        finally:
            if child.poll() is None:
                child.kill()
                child.wait(timeout=10)
            reader.join(timeout=2)
        assert child.stderr.read() == b""


@pytest.mark.parametrize("stage,task_status,accepted,request_id", [
    ("saved-before-wake", "CREATED", False, None),
    ("claimed-before-send", "SUBMITTED", False, None),
    ("accepted-before-response", "SUBMITTED", True, None),
    ("response-before-ack", "SUBMITTED", True, "fixture-request"),
])
def test_process_death_preserves_task_and_distinguishes_external_acceptance(database, stage, task_status,
                                                                         accepted, request_id):
    crash_at(database, stage)
    # Read from another client after the writer was killed, not its in-memory objects.
    with MongoClient(os.environ["MONGODB_URI"], serverSelectionTimeoutMS=3000) as reader:
        stored = reader[database.name].fcTask.find_one({"_id": TASK})
        assert stored["status"] == task_status
        assert stored["attemptId"] == "fixture-attempt"
        assert stored["attemptCount"] == 1 and stored["maxAttempts"] == 2
        assert stored["payloadSnapshot"] == {"videoId": "fixture-crash", "operation": "PROBE"}
        assert stored["executionTimeoutMs"] == 300000 and stored["deadline"] > stored["createTime"]
        assert stored.get("fcRequestId") == request_id
        assert reader[database.name].syntheticProvider.count_documents({}) == int(accepted)
        assert reader[database.name].syntheticWake.count_documents({}) == 0


def test_notification_first_leaves_recoverable_intent_but_missing_task_requires_explicit_consumer(database):
    crash_at(database, "wake-before-save")
    assert database.fcTask.count_documents({}) == 0
    intent = database.syntheticWake.find_one({"_id": TASK})
    assert intent["payload"]["attemptId"] == "fixture-attempt"
    # Mere wakeups cannot recover absent tasks: only a full durable intent can re-create them.
    database.fcTask.update_one({"_id": TASK}, {"$setOnInsert": intent["payload"]}, upsert=True)
    assert database.fcTask.find_one({"_id": TASK}) == intent["payload"]


def test_duplicate_delivery_after_process_death_claims_created_attempt_once(database):
    crash_at(database, "saved-before-wake")
    before = database.fcTask.find_one({"_id": TASK})

    def claim(_):
        return database.fcTask.update_one({"_id": TASK, "status": "CREATED", "attemptId": "fixture-attempt"},
                                          {"$set": {"status": "SUBMITTED"}}).modified_count

    with ThreadPoolExecutor(max_workers=8) as pool:
        assert sum(pool.map(claim, range(16))) == 1
    assert database.fcTask.find_one({"_id": TASK}) == before | {"status": "SUBMITTED"}
    assert database.syntheticProvider.count_documents({}) == 0


def test_claim_before_send_and_accepted_before_response_are_indistinguishable_in_business_store(database):
    crash_at(database, "claimed-before-send")
    unsent = database.fcTask.find_one({"_id": TASK})
    database.fcTask.delete_many({})
    crash_at(database, "accepted-before-response")
    accepted = database.fcTask.find_one({"_id": TASK})
    for name in ("createTime", "deadline"):
        unsent.pop(name)
        accepted.pop(name)
    assert unsent == accepted
    # A CAS prevents immediate duplicate sends but cannot identify which crash occurred.
    assert database.syntheticProvider.count_documents({}) == 1
