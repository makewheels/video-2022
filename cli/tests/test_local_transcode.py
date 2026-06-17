import json
from unittest.mock import patch, MagicMock

from video_cli import local_transcode as lt


def test_target_resolutions_1080p_source():
    # 1080p source -> both 720p and 1080p, mirroring the server
    assert lt.target_resolutions(1920, 1080) == ["720p", "1080p"]


def test_target_resolutions_720p_source():
    assert lt.target_resolutions(1280, 720) == ["720p"]


def test_target_resolutions_small_source_falls_back_to_480p():
    assert lt.target_resolutions(640, 360) == ["480p"]


def test_output_height_never_upscales_and_is_even():
    # 720p target on a 1080p source -> 720
    assert lt.output_height("720p", 1080) == 720
    # 1080p target on a 1080p source -> 1080
    assert lt.output_height("1080p", 1080) == 1080
    # 1080p target on a 900p source -> capped to source, even
    assert lt.output_height("1080p", 900) == 900
    # odd source height is rounded down to even
    assert lt.output_height("1080p", 721) == 720


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
