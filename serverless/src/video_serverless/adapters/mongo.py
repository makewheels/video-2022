"""Bounded read-only Mongo adapter, configured by the composition root."""

from threading import Lock
from time import perf_counter

from video_serverless.ports.probe import ProbeReading


class MongoPublicVideoProbe:
    def __init__(self, uri: str | None, database_name: str | None):
        self._uri = uri
        self._database_name = database_name
        self._client = None
        self._database = None
        self._lock = Lock()

    def database(self):
        if self._database is not None:
            return self._database
        with self._lock:
            if self._database is None:
                # No driver import or connection on the health/empty paths.
                from pymongo import MongoClient

                if not self._uri or not self._database_name:
                    raise RuntimeError("Mongo probe is not configured")
                self._client = MongoClient(
                    self._uri,
                    minPoolSize=0,
                    maxPoolSize=5,
                    maxIdleTimeMS=60_000,
                    serverSelectionTimeoutMS=3_000,
                    connectTimeoutMS=3_000,
                    socketTimeoutMS=3_000,
                    waitQueueTimeoutMS=1_000,
                    appname="video-serverless-prototype",
                )
                self._database = self._client[self._database_name]
        return self._database

    def read_public_video(self) -> ProbeReading:
        started = perf_counter()
        db = self.database()
        initialized = perf_counter()
        # Preserve the original single, bounded id projection; never expose a record.
        found = db.video.find_one({"visibility": "PUBLIC"}, {"_id": 1}, max_time_ms=1_000)
        finished = perf_counter()
        return ProbeReading(
            found_public_video=found is not None,
            client_setup_ms=round((initialized - started) * 1000, 3),
            read_ms=round((finished - initialized) * 1000, 3),
        )
