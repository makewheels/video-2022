"""FFmpeg execution and HLS validation shared by local and OSS workflows."""

import json
import math
import subprocess
from pathlib import Path

RESOLUTIONS = {"720p": (1280, 720), "1080p": (1920, 1080)}


def probe(path: Path, executable: str = "ffprobe", timeout: int = 30) -> dict:
    result = subprocess.run(
        [executable, "-v", "error", "-show_streams", "-show_format", "-of", "json", str(path)],
        check=True, capture_output=True, text=True, timeout=timeout,
    )
    return json.loads(result.stdout)


def command(source: Path, directory: Path, transcode_id: str, resolution: str,
            encoder: str = "h264_nvenc", executable: str = "ffmpeg") -> list[str]:
    if encoder not in {"h264_nvenc", "libx264"}:
        raise ValueError("Unsupported encoder")
    width, height = RESOLUTIONS[resolution]
    # Swap the bounding box for portrait; FFmpeg applies display rotation before filtering.
    box_width = f"if(gt(iw,ih),{width},{height})"
    box_height = f"if(gt(iw,ih),{height},{width})"
    ratio = f"min(1,min(({box_width})/iw,({box_height})/ih))"
    scale = f"scale=w='max(2,trunc(iw*{ratio}/2)*2)':h='max(2,trunc(ih*{ratio}/2)*2)',setsar=1"
    bitrate = "3000k" if resolution == "720p" else "5000k"
    preset = "p4" if encoder == "h264_nvenc" else "fast"
    keyframe_options = ["-forced-idr", "1"] if encoder == "h264_nvenc" else []
    return [
        executable, "-hide_banner", "-loglevel", "error", "-nostdin", "-y", "-i", str(source),
        "-map", "0:v:0", "-map", "0:a:0?", "-vf", scale,
        "-c:v", encoder, "-preset", preset, "-pix_fmt", "yuv420p", "-b:v", bitrate,
        "-maxrate", bitrate, "-bufsize", "10000k", "-force_key_frames", "expr:gte(t,n_forced*5)",
        *keyframe_options,
        "-c:a", "aac", "-b:a", "128k", "-f", "hls", "-hls_time", "5", "-hls_list_size", "0",
        "-hls_playlist_type", "vod", "-hls_segment_filename", str(directory / f"{transcode_id}-%05d.ts"),
        str(directory / f"{transcode_id}.m3u8"),
    ]


def segments(content: str, transcode_id: str) -> list[str]:
    import re

    lines = [line.strip() for line in content.splitlines() if line.strip()]
    if not lines or lines[0] != "#EXTM3U" or lines[-1] != "#EXT-X-ENDLIST":
        raise ValueError("Incomplete HLS playlist")
    names = []
    duration_pending = False
    for line in lines:
        if line.startswith("#EXTINF:"):
            if duration_pending:
                raise ValueError("Missing segment URI")
            duration = float(line.split(":", 1)[1].split(",", 1)[0])
            if not math.isfinite(duration) or duration <= 0:
                raise ValueError("Invalid segment duration")
            duration_pending = True
        elif not line.startswith("#"):
            if not duration_pending or not re.fullmatch(re.escape(transcode_id) + r"-\d{5,}\.ts", line):
                raise ValueError("Invalid HLS segment")
            names.append(line)
            duration_pending = False
    if not names or duration_pending or len(names) != len(set(names)):
        raise ValueError("Invalid HLS segment list")
    return names


def transcode(source: Path, directory: Path, transcode_id: str, resolution: str,
              encoder: str = "h264_nvenc", timeout: int = 3600) -> list[Path]:
    directory.mkdir(parents=True, exist_ok=True)
    subprocess.run(command(source, directory, transcode_id, resolution, encoder),
                   check=True, capture_output=True, timeout=timeout)
    playlist = directory / f"{transcode_id}.m3u8"
    files = [directory / name for name in segments(playlist.read_text(), transcode_id)]
    if any(not path.is_file() or path.stat().st_size == 0 for path in files):
        raise ValueError("Missing or empty HLS segment")
    return [*files, playlist]
