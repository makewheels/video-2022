"""自建函数本地冒烟测试：不依赖云端，直接调用 handler（OSS 用本地文件桩）。

覆盖：PROBE（SDR/HDR 识别）、COVER（含 HDR tone-map）、remux、NVENC 编码路径
（本地无 GPU，encoder 参数桩替换为 libx264/libx265 验证命令构建与 HLS 产物）。
"""
import json
import os
import subprocess
from types import SimpleNamespace

import pytest

import sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from common import media  # noqa: E402
from cpu import cover, probe, remux  # noqa: E402
from gpu import transcode as gpu_transcode  # noqa: E402

FFMPEG = "ffmpeg"


def _has_ffmpeg() -> bool:
    try:
        subprocess.run(["ffmpeg", "-version"], capture_output=True, check=True)
        return True
    except (FileNotFoundError, subprocess.CalledProcessError):
        return False


pytestmark = pytest.mark.skipif(not _has_ffmpeg(), reason="本机无 ffmpeg")


@pytest.fixture(scope="module")
def sdr_sample(tmp_path_factory):
    path = tmp_path_factory.mktemp("sdr") / "sdr.mp4"
    subprocess.run([
        FFMPEG, "-y",
        "-f", "lavfi", "-i", "testsrc=duration=4:size=1280x720:rate=30",
        "-f", "lavfi", "-i", "sine=frequency=440:duration=4",
        "-c:v", "libx264", "-pix_fmt", "yuv420p",
        "-g", "30", "-keyint_min", "30", "-sc_threshold", "0",
        "-c:a", "aac", "-b:a", "128k",
        "-shortest", str(path),
    ], capture_output=True, check=True)
    return str(path)


@pytest.fixture(scope="module")
def hdr_sample(tmp_path_factory):
    path = tmp_path_factory.mktemp("hdr") / "hdr.mp4"
    subprocess.run([
        FFMPEG, "-y",
        "-f", "lavfi", "-i", "testsrc=duration=2:size=1280x720:rate=30",
        "-f", "lavfi", "-i", "sine=frequency=440:duration=2",
        "-c:v", "libx265", "-pix_fmt", "yuv420p10le",
        "-x265-params", "colorprim=bt2020:transfer=smpte2084:colormatrix=bt2020nc",
        "-c:a", "aac", "-shortest", str(path),
    ], capture_output=True, check=True)
    return str(path)


class FakeOss:
    def __init__(self, root: str):
        self.root = root
        self.uploaded = {}

    def download(self, key, local_path):
        src = os.path.join(self.root, key)
        subprocess.run(["cp", src, local_path], check=True)

    def upload_file(self, local_path, key):
        self.uploaded[key] = os.path.getsize(local_path)

    def upload_text(self, text, key):
        self.uploaded[key] = len(text.encode("utf-8"))

    def exists(self, key):
        return key in self.uploaded

    def head(self, key):
        return SimpleNamespace(content_length=self.uploaded[key])


def _body(tmp_path, op, task_id="task_t", input_key="videos/u/202610/v/raw/v.mp4",
          **extra):
    body = {
        "schemaVersion": 1,
        "taskId": task_id,
        "attemptId": "attempt_1",
        "operation": op,
        "videoId": "v",
        "transcodeId": "tr_1" if op == "TRANSCODE" else None,
        "bucket": "b",
        "endpoint": "e",
        "inputKey": input_key,
        "outputDir": "videos/u/202610/v/transcode/tr_1",
        "callbackUrl": "http://callback.example/callback",
    }
    body.update(extra)
    return body


# ---------------- PROBE ----------------

def test_probe_sdr_media_info(sdr_sample, tmp_path):
    info = media.build_media_info(sdr_sample)
    assert info["width"] == 1280 and info["height"] == 720
    assert info["videoCodec"] == "h264"
    assert info["audioCodec"] == "aac"
    assert info["hasAudio"] is True
    assert info["dynamicRange"] == "SDR"
    assert info["bitDepth"] == 8
    assert info["audioTrackCount"] == 1
    assert info["durationMs"] >= 3800


def test_probe_hdr_identification(hdr_sample, tmp_path):
    info = media.build_media_info(hdr_sample)
    assert info["dynamicRange"] == "HDR10"
    assert info["bitDepth"] == 10
    assert info["colorPrimaries"] == "bt2020"
    assert info["colorTransfer"] == "smpte2084"


def test_probe_manifest_shape(sdr_sample, tmp_path):
    manifest = probe.handle_probe(sdr_sample, str(tmp_path), None,
                                  _body(tmp_path, "PROBE"))
    assert manifest["schemaVersion"] == 1
    assert manifest["operation"] == "PROBE"
    assert manifest["mediaInfo"]["width"] == 1280


# ---------------- COVER ----------------

def test_cover_sdr(sdr_sample, tmp_path):
    oss = FakeOss(str(tmp_path))
    body = _body(tmp_path, "COVER",
                 imageKey="videos/u/202610/v/cover/c1/f1.jpg")
    manifest = cover.handle_cover(sdr_sample, str(tmp_path), oss, body)
    assert manifest["imageKey"] in oss.uploaded
    assert manifest["width"] > 0 and manifest["height"] > 0
    assert manifest["sourceDynamicRange"] == "SDR"


def test_cover_hdr_tone_map_requires_filter(hdr_sample, tmp_path):
    """HDR 封面必须 tone-map：环境缺 zscale/libplacebo 时明确失败，不静默输出错误颜色。"""
    oss = FakeOss(str(tmp_path))
    body = _body(tmp_path, "COVER",
                 imageKey="videos/u/202610/v/cover/c2/f2.jpg")
    try:
        manifest = cover.handle_cover(hdr_sample, str(tmp_path), oss, body)
        # 有滤镜的环境（如云端镜像）直接成功
        assert manifest["imageKey"] in oss.uploaded
        assert manifest["sourceDynamicRange"] == "HDR10"
    except RuntimeError as e:
        assert "tone-map" in str(e)
        assert not oss.uploaded


# ---------------- remux（CPU TRANSCODE） ----------------

def test_remux_produces_hls(sdr_sample, tmp_path):
    oss = FakeOss(str(tmp_path))
    body = _body(tmp_path, "TRANSCODE", playlistKey="videos/u/202610/v/transcode/tr_1/tr_1.m3u8")
    body["mediaPolicy"] = {"reencode": False, "segmentType": "ts", "segmentSeconds": 2}
    manifest = remux.handle_remux(sdr_sample, str(tmp_path), oss, body)
    assert manifest["playlistKey"] == body["playlistKey"]
    segment_count = sum(1 for o in manifest["objects"] if o["type"] == "segment")
    assert segment_count >= 2
    assert manifest["hlsCodecs"].startswith("avc1.")
    assert manifest["frameRate"] == "30/1"


def test_remux_rejects_reencode(sdr_sample, tmp_path):
    body = _body(tmp_path, "TRANSCODE", playlistKey="videos/u/202610/v/transcode/tr_1/tr_1.m3u8")
    body["mediaPolicy"] = {"reencode": True}
    with pytest.raises(RuntimeError, match="reencode"):
        remux.handle_remux(sdr_sample, str(tmp_path), None, body)


# ---------------- GPU TRANSCODE ----------------

@pytest.fixture
def stub_encoder(monkeypatch):
    """本地无 NVENC：把 encoder 参数桩替换为 libx264/libx265，验证命令构建与 HLS 产物。"""
    def fake_args(policy, is_hdr):
        if is_hdr:
            return ["-c:v", "libx265", "-profile:v", "main10", "-pix_fmt", "yuv420p10le",
                    "-tag:v", "hvc1", "-x265-params",
                    "colorprim=bt2020:transfer=smpte2084:colormatrix=bt2020nc"]
        return ["-c:v", "libx264", "-pix_fmt", "yuv420p"]

    monkeypatch.setattr(gpu_transcode, "_video_encoder_args", fake_args)


def _gpu_body(tmp_path, policy, playlist_key="videos/u/202610/v/transcode/tr_g/tr_g.m3u8"):
    body = _body(tmp_path, "TRANSCODE", playlistKey=playlist_key)
    body["mediaPolicy"] = policy
    return body


def test_gpu_sdr_two_lanes(sdr_sample, tmp_path, stub_encoder):
    for profile, width in (("720p", 1280), ("1080p", 1280)):
        key = f"videos/u/202610/v/transcode/tr_{profile}/tr_{profile}.m3u8"
        oss = FakeOss(str(tmp_path))
        policy = {
            "outputWidth": width, "outputHeight": 720,
            "dynamicRange": "SDR", "videoCodec": "h264", "videoEncoder": "h264_nvenc",
            "frameRateMode": "preserve", "segmentType": "ts", "segmentSeconds": 2,
            "targetBitrateBps": 4000000, "maxBitrateBps": 6000000, "bufferBits": 12000000,
            "reencode": True,
            "audioPolicy": {"mode": "encode", "codec": "aac", "preserveTracks": True},
        }
        manifest = gpu_transcode.handle_transcode(
            sdr_sample, str(tmp_path), oss, _gpu_body(tmp_path, policy, key))
        assert manifest["playlistKey"] == key
        assert manifest["hlsCodecs"].startswith("avc1.")
        assert manifest["dynamicRange"] == "SDR"
        assert manifest["audioTracks"]["tracks"], "不应丢失音轨"
        assert manifest["averageBitrateBps"] > 0
        assert any(o["key"].endswith(".m3u8") for o in manifest["objects"])
        # 无音轨不会造假音频 codec
        assert manifest["hlsCodecs"].endswith("mp4a.40.2")


def test_gpu_hdr_fmp4(hdr_sample, tmp_path, stub_encoder):
    oss = FakeOss(str(tmp_path))
    policy = {
        "outputWidth": 1280, "outputHeight": 720,
        "dynamicRange": "HDR10", "videoCodec": "hevc", "videoEncoder": "hevc_nvenc",
        "frameRateMode": "preserve", "segmentType": "fmp4", "segmentSeconds": 2,
        "targetBitrateBps": 4000000, "maxBitrateBps": 6000000, "bufferBits": 12000000,
        "reencode": True,
        "colorPrimaries": "bt2020", "colorTransfer": "smpte2084", "colorSpace": "bt2020nc",
        "audioPolicy": {"mode": "encode", "codec": "aac", "preserveTracks": True},
    }
    manifest = gpu_transcode.handle_transcode(
        hdr_sample, str(tmp_path), oss,
        _gpu_body(tmp_path, policy, "videos/u/202610/v/transcode/tr_h/tr_h.m3u8"))
    assert manifest["bitDepth"] == 10
    assert manifest["hlsCodecs"].startswith("hvc1.")
    playlist_key = manifest["playlistKey"]
    playlist_name = os.path.basename(playlist_key)
    with open(os.path.join(str(tmp_path), "hls", playlist_name)) as f:
        content = f.read()
    assert "#EXT-X-MAP" in content, "fMP4 必须有 init segment 映射"
    assert "#EXT-X-ENDLIST" in content


def test_gpu_rejects_preserve_tracks_false(sdr_sample, tmp_path, stub_encoder):
    policy = {
        "outputWidth": 1280, "outputHeight": 720,
        "dynamicRange": "SDR", "videoCodec": "h264", "videoEncoder": "h264_nvenc",
        "segmentType": "ts", "segmentSeconds": 2,
        "targetBitrateBps": 4000000, "maxBitrateBps": 6000000, "bufferBits": 12000000,
        "reencode": True,
        "audioPolicy": {"mode": "encode", "codec": "aac", "preserveTracks": False},
    }
    with pytest.raises(RuntimeError, match="preserveTracks"):
        gpu_transcode.handle_transcode(sdr_sample, str(tmp_path), FakeOss(str(tmp_path)),
                                       _gpu_body(tmp_path, policy))
