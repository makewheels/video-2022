import json
from unittest.mock import patch, MagicMock

from video_cli import local_transcode as lt


def test_target_resolutions_1080p_source():
    # 1080p source -> both 720p and 1080p, mirroring the server
    assert lt.target_resolutions(1920, 1080) == ["720p", "1080p"]


def test_target_resolutions_720p_source():
    assert lt.target_resolutions(1280, 720) == ["720p"]


def test_target_resolutions_small_source_single_720p_lane_no_480p():
    # 新规则：低清单档 720p 标签原尺寸，不再生成 480p
    assert lt.target_resolutions(640, 360) == ["720p"]


def test_target_resolutions_portrait():
    # 1080x1920 竖屏：短边 1080 → 两档
    assert lt.target_resolutions(1080, 1920) == ["720p", "1080p"]


def test_output_size_short_side_rule_and_even_alignment():
    # 720p target on a 1080p source -> short side 720
    assert lt.output_size(1920, 1080, "720p") == (1280, 720)
    # 1080p target on a 1080p source -> no scale
    assert lt.output_size(1920, 1080, "1080p") == (1920, 1080)
    # 1080p target on a 900p source -> never upscale
    assert lt.output_size(1600, 900, "1080p") == (1600, 900)
    # portrait: 720p lane -> 720x1280
    assert lt.output_size(1080, 1920, "720p") == (720, 1280)
    # odd dimensions are even-aligned
    w, h = lt.output_size(1281, 721, "720p")
    assert w % 2 == 0 and h % 2 == 0
    # sub-720p source keeps size under 720p label (no upscale)
    assert lt.output_size(640, 360, "720p") == (640, 360)


def test_probe_parses_ffprobe_json():
    ffprobe_json = json.dumps({
        "streams": [
            {"codec_type": "video", "codec_name": "h264", "width": 1920, "height": 1080},
            {"codec_type": "audio", "codec_name": "aac"},
        ],
        "format": {"duration": "4067.625", "bit_rate": "738334"},
    })
    completed = MagicMock(stdout=ffprobe_json)
    with patch("video_cli.local_transcode.subprocess.run", return_value=completed):
        info = lt.probe("whatever.mp4")
    assert info["width"] == 1920
    assert info["height"] == 1080
    assert info["duration_ms"] == 4067625
    assert info["video_codec"] == "h264"
    assert info["audio_codec"] == "aac"
    assert info["bitrate_kbps"] == 738
    assert info["has_audio"] is True


def test_probe_handles_no_audio_stream():
    ffprobe_json = json.dumps({
        "streams": [
            {"codec_type": "video", "codec_name": "h264", "width": 1280, "height": 720},
        ],
        "format": {"duration": "10.0", "bit_rate": "500000"},
    })
    completed = MagicMock(stdout=ffprobe_json)
    with patch("video_cli.local_transcode.subprocess.run", return_value=completed):
        info = lt.probe("silent.mp4")
    assert info["audio_codec"] is None
    assert info["has_audio"] is False
