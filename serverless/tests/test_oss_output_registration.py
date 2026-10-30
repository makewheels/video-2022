"""Explicit object SDK boundaries, all pages, closed streams and only recoverable optional reads."""

from datetime import UTC, datetime
from types import SimpleNamespace

import pytest

from video_serverless.adapters.oss_output_registration import OssOutputRegistration


def test_all_1000_key_pages_include_sidecar_and_nonmedia_without_filtering():
    calls = []

    class Bucket:
        def list_objects_v2(self, **parameters):
            calls.append(parameters)
            first = parameters["continuation_token"] == ""
            names = [str(index) + ".ts" for index in range(999)] + ["result.json"] if first else ["notes.txt"]
            return SimpleNamespace(
                object_list=[SimpleNamespace(key="fixture/" + name, size=1, etag="fixture-etag",
                                             storage_class="Archive", last_modified=2) for name in names],
                is_truncated=first, next_continuation_token="second-page",
            )

    outputs = OssOutputRegistration(Bucket(), None, None).list_outputs("fixture/")
    assert len(outputs) == 1001 and outputs[-2].key == "fixture/result.json" and outputs[-1].key == "fixture/notes.txt"
    assert outputs[-1].storage_class == "Archive"
    assert outputs[-1].upload_time == datetime(1970, 1, 1, 0, 0, 2, tzinfo=UTC)
    assert calls == [{"prefix": "fixture/", "continuation_token": token, "max_keys": 1000}
                     for token in ("", "second-page")]


def test_later_page_failure_is_not_returned_as_a_successful_partial_listing():
    calls = []

    class Bucket:
        def list_objects_v2(self, **parameters):
            calls.append(parameters["continuation_token"])
            if len(calls) == 2:
                raise OSError("synthetic second-page failure")
            return SimpleNamespace(object_list=[], is_truncated=True, next_continuation_token="second")

    with pytest.raises(OSError, match="synthetic second-page failure"):
        OssOutputRegistration(Bucket(), None, None).list_outputs("fixture/")
    assert calls == ["", "second"]


@pytest.mark.parametrize("failed", [False, True])
def test_metadata_reads_headers_without_media_body_and_always_closes(failed):
    closed = []

    class Result:
        content_length, last_modified, headers = 123, 2, {}

        @property
        def etag(self):
            if failed:
                raise OSError("synthetic metadata failure")
            return "fixture-etag"

        def read(self):
            pytest.fail("Metadata must not buffer media")

        def close(self):
            closed.append(True)

    bucket = SimpleNamespace(get_object=lambda key: Result())
    objects = OssOutputRegistration(bucket, None, None)
    if failed:
        with pytest.raises(OSError, match="synthetic metadata failure"):
            objects.metadata("fixture/playlist.m3u8")
    else:
        metadata = objects.metadata("fixture/playlist.m3u8")
        assert metadata.size == 123 and metadata.storage_class == "Standard"
    assert closed == [True]


@pytest.mark.parametrize("kind", ["io", "sdk", "programming"])
def test_optional_sidecar_warns_only_for_known_read_failures_and_surfaces_programming_errors(kind):
    from oss2.exceptions import OssError

    warnings = []
    error = {"io": OSError("synthetic read failure"), "sdk": OssError(503, {}, b"synthetic read failure", {}),
             "programming": RuntimeError("synthetic programming failure")}[kind]

    class Bucket:
        def get_object(self, key):
            raise error

    objects = OssOutputRegistration(Bucket(), None, warnings.append)
    if kind == "programming":
        with pytest.raises(RuntimeError, match="synthetic programming failure"):
            objects.read_sidecar("fixture/result.json")
        assert not warnings
    else:
        assert objects.read_sidecar("fixture/result.json") is None
        assert len(warnings) == 1 and "读取对象失败" in warnings[0]


@pytest.mark.parametrize("text", ["null", "{"])
def test_sidecar_parse_failure_is_not_treated_as_optional_absence(text):
    closed, warnings = [], []
    result = SimpleNamespace(read=lambda: text.encode(), close=lambda: closed.append(True))
    objects = OssOutputRegistration(SimpleNamespace(get_object=lambda key: result), None, warnings.append)
    with pytest.raises(ValueError):
        objects.read_sidecar("fixture/result.json")
    assert closed == [True] and not warnings
