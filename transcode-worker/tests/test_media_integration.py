"""Actual FFmpeg checks with all generated media under the OS temporary directory."""

import shutil
import subprocess
import tempfile
from pathlib import Path

import pytest

from video_transcode_worker.media import probe, transcode


@pytest.mark.skipif(not shutil.which("ffmpeg") or not shutil.which("ffprobe"), reason="FFmpeg not installed")
@pytest.mark.parametrize("size,audio", [("1920x1080", True), ("1080x1920", False), ("320x240", False)])
def test_real_hls_preserves_orientation_audio_and_does_not_upscale(size, audio):
    with tempfile.TemporaryDirectory(prefix="video-gpu-media-test-") as directory:
        root = Path(directory)
        source = root / "input.mp4"
        cmd = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i",
               f"testsrc2=size={size}:rate=24:duration=6"]
        if audio:
            cmd += ["-f", "lavfi", "-i", "sine=frequency=1000:duration=6", "-c:a", "aac"]
        cmd += ["-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", str(source)]
        subprocess.run(cmd, check=True, capture_output=True, timeout=60)
        files = transcode(source, root / "out", "test", "720p", "libx264", timeout=60)
        assert len(files) >= 3  # At least two segments plus playlist; exercise a segment boundary.
        # Decode all segments through the playlist, not just its metadata.
        subprocess.run(["ffmpeg", "-v", "error", "-i", str(files[-1]), "-f", "null", "-"],
                       check=True, capture_output=True, timeout=60)
        streams = probe(files[0])["streams"]
        video = next(stream for stream in streams if stream["codec_type"] == "video")
        expected = {"1920x1080": (1280, 720), "1080x1920": (720, 1280), "320x240": (320, 240)}[size]
        assert (video["width"], video["height"]) == expected
        assert video["codec_name"] == "h264"
        assert any(stream["codec_type"] == "audio" and stream["codec_name"] == "aac"
                   for stream in streams) == audio
