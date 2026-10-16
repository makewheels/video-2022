"""Real old validator corpus compared with ordinary rules and a synthetic explicit SDK bucket."""

import json
from pathlib import Path

import pytest

from video_serverless.adapters.oss_output_objects import OssOutputObjects
from video_serverless.business.output_validation import OutputValidation
from video_serverless.ports.output_validation import OutputManifest, OutputObject

CASES_PATH = Path(__file__).parents[1] / "contracts/output-validation/cases.json"


class Bucket:
    def __init__(self, case):
        self.case, self.calls, self.reads, self.closed = case, [], [], []

    def get_object(self, key):
        self.calls.append(key)
        if key == self.case["failedKey"] and self.calls.count(key) == self.case["failedCall"]:
            raise RuntimeError("synthetic read failure")
        if key not in self.case["sizes"]:
            return None
        bucket = self

        class Result:
            content_length = bucket.case["sizes"][key]

            def read(self):
                bucket.reads.append(key)
                return bucket.case["playlist"].encode() if key == bucket.case["key"] else b"x"

            def close(self):
                bucket.closed.append(key)

        return Result()


@pytest.mark.parametrize("case", json.loads(CASES_PATH.read_text(encoding="utf-8")), ids=lambda row: row["scenario"])
def test_same_error_and_object_call_order_as_real_java_validator(case):
    bucket = Bucket(case)
    raw = case["manifest"]
    manifest = OutputManifest(raw.get("playlistKey"), None if "objects" not in raw else tuple(
        OutputObject(item.get("key"), item.get("size", 0)) for item in raw["objects"]))
    assert OutputValidation(OssOutputObjects(bucket)).validate(case["key"], manifest) == case["error"]
    assert bucket.calls == case["calls"]
    assert not bucket.reads or bucket.reads == [case["key"]]
    successful = [key for index, key in enumerate(bucket.calls) if key in case["sizes"] and not (
        key == case["failedKey"] and bucket.calls[:index + 1].count(key) == case["failedCall"])]
    assert bucket.closed == successful


@pytest.mark.parametrize("operation", ["read_text", "size"])
def test_object_result_is_closed_even_when_read_or_header_access_fails(operation):
    closed = []

    class Result:
        @property
        def content_length(self):
            raise RuntimeError("synthetic metadata failure")

        def read(self):
            raise RuntimeError("synthetic body failure")

        def close(self):
            closed.append(True)

    class ExplicitBucket:
        def get_object(self, key):
            return Result()

    with pytest.raises(RuntimeError):
        getattr(OssOutputObjects(ExplicitBucket()), operation)("fixture-key")
    assert closed == [True]


def test_text_decode_replaces_invalid_utf8_and_size_never_consumes_object_body():
    reads, closed = [], []

    class Result:
        content_length = 123

        def read(self):
            reads.append(True)
            return b"\xff"

        def close(self):
            closed.append(True)

    class ExplicitBucket:
        def get_object(self, key):
            return Result()

    objects = OssOutputObjects(ExplicitBucket())
    assert objects.size("fixture-key") == 123
    assert reads == [] and closed == [True]
    assert objects.read_text("fixture-key") == "\ufffd"
    assert reads == [True] and closed == [True, True]
