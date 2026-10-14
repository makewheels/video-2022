import json
from concurrent.futures import ThreadPoolExecutor
from time import sleep

import pytest

from video_serverless.adapters.mongo import MongoPublicVideoProbe


def test_probe_has_one_bounded_read_and_does_not_return_record(monkeypatch):
    calls = []

    class Collection:
        def find_one(self, *args, **kwargs):
            calls.append((args, kwargs))
            return {"_id": "private-record"}

    class Database:
        video = Collection()

    probe = MongoPublicVideoProbe(None, None)
    monkeypatch.setattr(probe, "database", lambda: Database())
    result = probe.read_public_video()
    assert calls == [(({"visibility": "PUBLIC"}, {"_id": 1}), {"max_time_ms": 1000})]
    assert result.found_public_video is True
    assert "private-record" not in json.dumps(vars(result))


def test_client_created_once_under_concurrency_and_pool_reused(monkeypatch):
    calls = []
    db = object()

    class Client:
        def __init__(self, *args, **kwargs):
            calls.append((args, kwargs))
            sleep(0.01)  # Overlap first callers; a plain cache decorator would race.

        def __getitem__(self, name):
            assert name == "isolated-test"
            return db

    monkeypatch.setattr("pymongo.MongoClient", Client)
    probe = MongoPublicVideoProbe("mongodb://localhost:27017", "isolated-test")
    with ThreadPoolExecutor(max_workers=16) as pool:
        results = list(pool.map(lambda _: probe.database(), range(32)))
    assert all(item is db for item in results)
    assert probe.database() is db
    assert len(calls) == 1
    assert calls[0][0] == ("mongodb://localhost:27017",)
    assert calls[0][1]["maxPoolSize"] == 5
    assert calls[0][1]["minPoolSize"] == 0
    assert calls[0][1]["serverSelectionTimeoutMS"] == 3000


@pytest.mark.parametrize("uri,name", [(None, None), ("mongodb://localhost", None), (None, "isolated")])
def test_missing_configuration_cannot_create_client(monkeypatch, uri, name):
    monkeypatch.setattr("pymongo.MongoClient", lambda *a, **k: pytest.fail("must not create a client"))
    with pytest.raises(RuntimeError, match="Mongo probe is not configured"):
        MongoPublicVideoProbe(uri, name).database()
