"""Test-only model of FcTask write ordering; never imports or calls a cloud client."""

import sys
from datetime import UTC, datetime, timedelta

from pymongo import MongoClient
from pymongo.uri_parser import parse_uri
from pymongo.write_concern import WriteConcern


def main():
    uri, database_name, stage = sys.argv[1:]
    parsed = parse_uri(uri)
    assert not parsed["username"] and not parsed["password"]
    assert all(host in {"localhost", "127.0.0.1", "::1"} for host, _ in parsed["nodelist"])
    assert database_name.startswith("video-user-read-test-")
    with MongoClient(uri, serverSelectionTimeoutMS=3000) as client:
        database = client.get_database(database_name, write_concern=WriteConcern(w=1, j=True))
        now = datetime.now(UTC)
        task = {
            "_id": "fc-PROBE-fixture-crash", "operation": "PROBE", "videoId": "fixture-crash",
            "refId": None, "provider": "CPU", "attemptId": "fixture-attempt", "attemptCount": 1,
            "maxAttempts": 2, "status": "CREATED", "deadline": now + timedelta(minutes=5),
            "createTime": now, "payloadSnapshot": {"videoId": "fixture-crash", "operation": "PROBE"},
            "outputBaseDir": "fixture/crash", "executionTimeoutMs": 300000,
        }
        # This collection represents durable notification storage for the experiment,
        # not a production queue, outbox schema or proof of any cloud delivery guarantee.
        if stage == "wake-before-save":
            database.syntheticWake.insert_one({"_id": task["_id"], "payload": task})
        else:
            database.fcTask.insert_one(task)
            if stage in {"claimed-before-send", "accepted-before-response", "response-before-ack"}:
                database.fcTask.update_one({"_id": task["_id"], "status": "CREATED"},
                                           {"$set": {"status": "SUBMITTED"}})
            if stage in {"accepted-before-response", "response-before-ack"}:
                database.syntheticProvider.insert_one({"_id": "fixture-request", "taskId": task["_id"],
                                                        "attemptId": task["attemptId"]})
            if stage == "response-before-ack":
                database.fcTask.update_one({"_id": task["_id"]}, {"$set": {"fcRequestId": "fixture-request"}})
        # The parent kills us only after journaled writes complete, without graceful close.
        print("checkpoint", flush=True)
        sys.stdin.buffer.read(1)


if __name__ == "__main__":
    main()
