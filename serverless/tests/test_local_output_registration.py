"""Real Java internal-prefix BSON, object ordering, ordered partial writes and isolated assembly."""

import json
from pathlib import Path
from types import SimpleNamespace

import pytest
from bson import Int64
from test_local_covers import NOW
from test_user_mongo_reads import isolated_database as isolated_database
from test_video_reactions import normalize, restore

from video_serverless.adapters.mongo_output_registration import MongoOutputRegistration
from video_serverless.business.local_output_registration import LocalOutputRegistration
from video_serverless.local_output_bootstrap import build_local_output_registration

CASES = json.loads((Path(__file__).parents[1] / "contracts/local-output-registration/cases.json")
                   .read_text(encoding="utf-8"))
COLLECTIONS = ("video", "file", "tsFile", "transcode")


class Ids:
    def __init__(self):
        self.prefixes, self.index = [], 0

    def long_id(self, prefix):
        self.prefixes.append(prefix)
        if prefix == "f_":
            return "f_fixture_created"
        assert prefix == "f_ts_"
        result = "f_ts_fixture_created_" + str(self.index)
        self.index += 1
        return result


class Bucket:
    def __init__(self, case):
        self.input, self.calls, self.opened, self.closed, self.reads = case["input"], [], [], [], []

    def get_object(self, key):
        self.calls.append("get:" + key)
        count = self.calls.count("get:" + key)
        if key == self.input["failedKey"] and count == self.input["failedCall"]:
            raise OSError("synthetic object failure")
        if key == self.input["key"] and count == 2 and self.input["metadataNull"]:
            return None
        sidecar = key.endswith("result.json")
        content = self.input["sidecar"] if sidecar else self.input["playlist"] if key == self.input["key"] else "x"
        if content is None or (not sidecar and key not in self.input["sizes"]):
            return None
        self.opened.append(key)
        bucket = self

        class Result:
            content_length = len(content) if sidecar else bucket.input["sizes"][key]
            etag, last_modified, headers = "fixture-etag", 2, {"x-oss-storage-class": "Standard"}

            def read(self):
                bucket.reads.append(key)
                return content.encode()

            def close(self):
                bucket.closed.append(key)

        return Result()

    def sign_url(self, method, key, seconds):
        assert method == "GET"
        self.calls.append("sign:" + key + ":" + str(seconds))
        return "https://example.invalid/fixture-playlist"

    def http_get(self, url):
        self.calls.append("download:" + url)
        if self.input["signedFailure"]:
            raise RuntimeError("synthetic signed failure")
        return self.input["signedPlaylist"]

    def list_objects_v2(self, *, prefix, continuation_token, max_keys):
        self.calls.append("list:" + prefix)
        assert continuation_token == "" and max_keys == 1000
        if self.input["listFailure"]:
            raise RuntimeError("synthetic listing failure")
        items = [SimpleNamespace(key=item["key"], size=item["size"], etag=item["etag"],
                                 storage_class=item["storageClass"], last_modified=item["uploadTime"] / 1000)
                 for item in self.input["summaries"]]
        return SimpleNamespace(object_list=items, is_truncated=False)


@pytest.fixture
def database(isolated_database):
    for name in (*COLLECTIONS, "fcTask", "requestLog", "exceptionLog"):
        isolated_database.drop_collection(name)
    return isolated_database


def seed(database, case):
    for name, documents in restore(case["before"]).items():
        for document in documents:
            if name == "video" and document.get("mediaInfo") is not None:
                media = document["mediaInfo"]
                if isinstance(media.get("duration"), int):
                    media["duration"] = Int64(media["duration"])
            if name == "tsFile" and document.get("size") is not None:
                document["size"] = Int64(document["size"])
        if documents:
            database[name].insert_many(documents)
    rules = {
        "file-write-failure": ("file", {"fileStatus": "REJECT"}),
        "batch-write-failure": ("tsFile", {"tsIndex": {"$ne": 1}}),
        "transcode-write-failure": ("transcode", {"m3u8Content": {"$exists": False}}),
    }
    if case["scenario"] in rules:
        collection, rule = rules[case["scenario"]]
        if collection not in database.list_collection_names():
            database.create_collection(collection)
        database.command({"collMod": collection, "validator": rule})


def state(database):
    return {name: list(database[name].find().sort("_id", 1)) for name in COLLECTIONS}


@pytest.mark.parametrize("case", CASES, ids=lambda row: row["scenario"])
def test_real_legacy_prefix_complete_bson_failures_and_object_order(database, case, caplog):
    seed(database, case)
    assert normalize(state(database), {}) == case["before"]
    bucket, ids = Bucket(case), Ids()
    registration = build_local_output_registration(lambda: database, bucket, bucket.http_get, ids, lambda: NOW)
    transcode = MongoOutputRegistration(lambda: database, lambda: NOW).transcode("fixture-transcode")
    if case["registered"]:
        registration.register(transcode)
    else:
        with pytest.raises(Exception) as raised:
            registration.register(transcode)
        if case["error"] is not None:
            assert str(raised.value) == case["error"]
    assert normalize(state(database), {}) == case["after"]
    assert bucket.calls == case["calls"]
    assert ids.prefixes == case["idPrefixes"]
    assert bucket.closed == bucket.opened
    assert all(key == case["input"]["key"] or key.endswith("result.json") for key in bucket.reads)
    for name in ("file", "tsFile"):
        for document in database[name].find({"fileStatus": "READY"}):
            assert isinstance(document["size"], Int64)
    for name in ("fcTask", "requestLog", "exceptionLog"):
        assert database[name].count_documents({}) == 0
    if case["scenario"] == "sidecar-read-failure":
        assert "读取对象失败" in caplog.text
    if case["scenario"] in {"missing-media", "missing-duration"}:
        assert "跳过码率计算" in caplog.text


@pytest.mark.parametrize("scenario", ["sidecar-ts", "fmp4", "history"])
def test_sequential_registration_preserves_all_existing_files_and_repository_order(database, scenario):
    case = next(row for row in CASES if row["scenario"] == scenario)
    seed(database, case)
    bucket, ids = Bucket(case), Ids()
    registration = build_local_output_registration(lambda: database, bucket, bucket.http_get, ids, lambda: NOW)
    store = MongoOutputRegistration(lambda: database, lambda: NOW)
    registration.register(store.transcode("fixture-transcode"))
    first_state, first_ids = state(database), ids.prefixes.copy()
    registration.register(store.transcode("fixture-transcode"))
    assert state(database) == first_state
    assert ids.prefixes == first_ids
    assert bucket.calls.count("get:" + case["input"]["key"]) == 3
    assert sum(call.startswith("download:") for call in bucket.calls) == 2
    assert database.transcode.find_one()["status"] == "TRANSCODING"
    assert "finishTime" not in database.transcode.find_one()
    if scenario == "history":
        assert database.transcode.find_one()["tsFileIds"][0] == "z-historical"


@pytest.mark.parametrize("failure", ["save_file", "insert_segments", "save_transcode"])
def test_storage_failure_is_not_replayed_or_compensated(database, failure):
    case = next(row for row in CASES if row["scenario"] == "sidecar-ts")
    seed(database, case)
    bucket, ids, calls = Bucket(case), Ids(), []
    registration = build_local_output_registration(lambda: database, bucket, bucket.http_get, ids, lambda: NOW)
    inner = MongoOutputRegistration(lambda: database, lambda: NOW)
    operations = ["save_file", "insert_segments", "save_transcode"]

    class Store:
        def __getattr__(self, name):
            method = getattr(inner, name)
            if name not in operations:
                return method

            def write(value):
                calls.append(name)
                if name == failure:
                    raise RuntimeError("synthetic registration persistence failure")
                return method(value)
            return write

    registration = LocalOutputRegistration(Store(), registration.objects, ids, lambda: NOW,
                                           lambda message: pytest.fail(message))
    with pytest.raises(RuntimeError, match="synthetic registration persistence failure"):
        registration.register(inner.transcode("fixture-transcode"))
    assert calls == operations[:operations.index(failure) + 1]
    assert database.file.count_documents({}) == (failure != "save_file")
    assert database.tsFile.count_documents({}) == (2 if failure == "save_transcode" else 0)
    assert normalize(state(database)["transcode"], {}) == case["before"]["transcode"]


def test_internal_factory_constructs_no_clients_and_touches_no_dependency():
    def unexpected(*args, **kwargs):
        pytest.fail("Internal factory must be lazy")

    class Unused:
        def __getattr__(self, name):
            unexpected()

    build_local_output_registration(unexpected, Unused(), unexpected, Unused(), unexpected)
