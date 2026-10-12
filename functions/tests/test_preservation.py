"""媒体保留及任务边界的回归测试。"""
import os
import sys

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from common import media, protocol  # noqa: E402
from common.job import upload_all_files  # noqa: E402
from gpu import transcode  # noqa: E402


def request():
    return {
        "schemaVersion": 1, "operation": "TRANSCODE", "taskId": "task1",
        "attemptId": "attempt1", "videoId": "video1", "bucket": "b", "endpoint": "e",
        "inputKey": "videos/u/video1/raw/source.mp4",
        "outputDir": "videos/u/video1/transcode/t/attempts/attempt1",
        "callbackUrl": "https://oneclick.video/transcode/cloudFunctionCallback",
    }


@pytest.mark.parametrize("change", [
    {"outputDir": "videos/u/other/transcode/t"},
    {"outputDir": "videos/u/video1/../other/transcode/t"},
    {"playlistKey": "videos/u/video1/raw/source.mp4"},
    {"callbackUrl": "https://example.org/transcode/cloudFunctionCallback"},
    {"taskId": "../escape"},
])
def test_request_rejects_wrong_scope(change):
    with pytest.raises(protocol.ProtocolError):
        protocol.validate_request({**request(), **change}, "test-secret", "test-secret")


def test_request_auth_is_per_request_and_fails_closed():
    assert protocol.validate_request(request(), "a", "a")
    with pytest.raises(protocol.ProtocolError):
        protocol.validate_request(request(), "a", "b")
    with pytest.raises(protocol.ProtocolError):
        protocol.validate_request(request(), "", "")


def test_static_and_dynamic_metadata_are_recognized():
    hdr = {"pix_fmt": "yuv420p10le", "side_data_list": [
        {"side_data_type": "Mastering display metadata", "max_luminance": "1000/1"},
        {"side_data_type": "HDR Dynamic Metadata SMPTE2094-40 (HDR10+)"},
    ]}
    kind, metadata = media._detect_dynamic_range(hdr, "smpte2084")
    assert kind == "HDR10_PLUS"
    assert metadata["Mastering display metadata"]["max_luminance"] == "1000/1"


def test_gpu_rejects_dolby_vision_before_encoding(monkeypatch, tmp_path):
    monkeypatch.setattr(media, "build_media_info", lambda _: {"dynamicRange": "DOLBY_VISION"})
    body = request()
    body["playlistKey"] = body["outputDir"] + "/test.m3u8"
    body["mediaPolicy"] = {
        "reencode": True, "videoEncoder": "hevc_nvenc", "dynamicRange": "DOLBY_VISION",
        "outputWidth": 1280, "outputHeight": 720, "targetBitrateBps": 4000000,
        "maxBitrateBps": 6000000, "bufferBits": 12000000,
    }
    with pytest.raises(media.UnsupportedMediaError, match="动态 HDR"):
        transcode.handle_transcode("source", str(tmp_path), None, body)


def test_hevc_codec_uses_actual_main10_profile():
    # hvcC: Main10, compatibility 4, level 123, constraint B0.
    stream = {"extradata": "\n00000000: 0102 2000 0000 b000 0000 0000 7b  ...."}
    assert media._hevc_codec_string(stream) == "hvc1.2.4.L123.B0"
    assert media._hevc_codec_string({}) is None


def test_playlist_is_published_last(tmp_path):
    for name in ["a.m3u8", "z.ts", "init.mp4"]:
        (tmp_path / name).write_bytes(b"media")
    uploaded = []

    class Oss:
        def upload_file(self, path, key):
            uploaded.append(key)

    objects = upload_all_files(Oss(), str(tmp_path), "output")
    assert uploaded[-1] == "output/a.m3u8"
    assert next(x for x in objects if x["key"].endswith("init.mp4"))["type"] == "init"


def test_aac_codec_follows_audio_specific_config():
    assert media._aac_codec_string({"extradata": "\n00000000: 2b92 08  ..."}) == "mp4a.40.5"
    assert media._aac_codec_string({"profile": "HE-AACv2"}) == "mp4a.40.29"
    assert media._aac_codec_string({}) is None
