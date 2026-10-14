"""Bounded read-only Mongo probe; credentials never become response content."""

import os
from threading import Lock
from time import perf_counter

_client = None
_database = None
_lock = Lock()


def database():
    global _client, _database
    if _database is not None:
        return _database
    with _lock:
        if _database is None:
            # Lazy import/connection: the empty endpoint must not depend on Mongo.
            from pymongo import MongoClient

            uri = os.environ.get("MONGODB_URI")
            name = os.environ.get("MONGODB_DATABASE")
            if not uri or not name:
                raise RuntimeError("Mongo probe is not configured")
            _client = MongoClient(
                uri,
                minPoolSize=0,
                maxPoolSize=5,
                maxIdleTimeMS=60_000,
                serverSelectionTimeoutMS=3_000,
                connectTimeoutMS=3_000,
                socketTimeoutMS=3_000,
                waitQueueTimeoutMS=1_000,
                appname="video-serverless-prototype",
            )
            _database = _client[name]
    return _database


def read_probe() -> dict:
    started = perf_counter()
    db = database()
    initialized = perf_counter()
    # Only an id projection, bounded server time. Never expose record ids or data.
    found = db.video.find_one({"visibility": "PUBLIC"}, {"_id": 1}, max_time_ms=1_000)
    finished = perf_counter()
    return {
        "foundPublicVideo": found is not None,
        "clientSetupMs": round((initialized - started) * 1000, 3),
        "readMs": round((finished - initialized) * 1000, 3),
    }
