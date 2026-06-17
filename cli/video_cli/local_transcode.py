"""Local FFmpeg transcoding pipeline for video-2022.

Transcodes a video on the local machine with FFmpeg and uploads the resulting
HLS (m3u8 + ts) to OSS, so the server never invokes paid cloud transcoding
(Aliyun MPS / cloud function). The server only registers the uploaded result.
"""
import json
import os
import shutil
import subprocess

# Resolution label -> output height (pixels). Mirrors server Resolution constants.
RESOLUTION_HEIGHTS = {"480p": 480, "720p": 720, "1080p": 1080}


def ensure_tools():
    """Raise if ffprobe/ffmpeg are not on PATH."""
    for tool in ("ffprobe", "ffmpeg"):
        if shutil.which(tool) is None:
            raise RuntimeError(f"{tool} not found in PATH. Install FFmpeg first (brew install ffmpeg).")


def probe(filepath):
    """Probe a media file with ffprobe.

    Returns a dict with width, height, duration_ms, video_codec, audio_codec,
    bitrate_kbps, has_audio.
    """
    cmd = ["ffprobe", "-v", "error", "-print_format", "json",
           "-show_format", "-show_streams", filepath]
    out = subprocess.run(cmd, capture_output=True, text=True, check=True).stdout
    data = json.loads(out)
    streams = data.get("streams", [])
    fmt = data.get("format", {})
    video = next((s for s in streams if s.get("codec_type") == "video"), None)
    audio = next((s for s in streams if s.get("codec_type") == "audio"), None)
    if video is None:
        raise RuntimeError("No video stream found in file")
    duration = float(fmt.get("duration") or video.get("duration") or 0)
    bit_rate = fmt.get("bit_rate") or video.get("bit_rate")
    return {
        "width": int(video["width"]),
        "height": int(video["height"]),
        "duration_ms": int(duration * 1000),
        "video_codec": video.get("codec_name"),
        "audio_codec": audio.get("codec_name") if audio else None,
        "bitrate_kbps": int(int(bit_rate) / 1000) if bit_rate else None,
        "has_audio": audio is not None,
    }


def target_resolutions(width, height):
    """Resolutions to produce. Mirrors server TranscodeLauncher.transcodeVideo.

    >854x480  -> 720p ; >1280x720 -> 1080p. For sources smaller than 480p we
    still emit a single 480p rendition so the video stays playable.
    """
    pixels = width * height
    targets = []
    if pixels > 854 * 480:
        targets.append("720p")
    if pixels > 1280 * 720:
        targets.append("1080p")
    if not targets:
        targets.append("480p")
    return targets


def output_height(resolution, source_height):
    """Output height for a rendition, capped to source height (never upscale)."""
    height = min(RESOLUTION_HEIGHTS.get(resolution, source_height), source_height)
    return height - (height % 2)  # h264 needs even dimensions


def transcode_hls(input_path, out_dir, transcode_id, height, has_audio):
    """Produce {transcode_id}.m3u8 + {transcode_id}-%05d.ts inside out_dir.

    ffmpeg runs with cwd=out_dir and relative names, so the m3u8 references ts
    segments by bare filename (e.g. ``{id}-00000.ts``) — exactly what the
    server's M3u8Util parser and OSS object listing expect.
    """
    m3u8_name = f"{transcode_id}.m3u8"
    seg_name = f"{transcode_id}-%05d.ts"
    cmd = ["ffmpeg", "-y", "-i", input_path,
           "-vf", f"scale=-2:{height}",
           "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-pix_fmt", "yuv420p"]
    if has_audio:
        cmd += ["-c:a", "aac", "-b:a", "128k"]
    else:
        cmd += ["-an"]
    cmd += ["-hls_time", "6", "-hls_playlist_type", "vod", "-hls_list_size", "0",
            "-hls_segment_filename", seg_name, "-f", "hls", m3u8_name]
    subprocess.run(cmd, cwd=out_dir, check=True,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return m3u8_name


def extract_cover(input_path, out_path, at_seconds):
    """Grab a single frame as the cover image."""
    cmd = ["ffmpeg", "-y", "-ss", str(at_seconds), "-i", input_path,
           "-frames:v", "1", "-q:v", "2", out_path]
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def _bucket(credentials):
    import oss2
    endpoint = credentials["endpoint"]
    if not endpoint.startswith("http"):
        endpoint = "https://" + endpoint
    auth = oss2.StsAuth(credentials["accessKeyId"], credentials["secretKey"],
                        credentials["sessionToken"])
    return oss2.Bucket(auth, endpoint, credentials["bucket"])


def upload_dir(credentials, local_dir, oss_prefix):
    """Upload every file in local_dir to oss_prefix/<filename>."""
    bucket = _bucket(credentials)
    prefix = oss_prefix.rstrip("/")
    for name in sorted(os.listdir(local_dir)):
        local_path = os.path.join(local_dir, name)
        if os.path.isfile(local_path):
            bucket.put_object_from_file(prefix + "/" + name, local_path)


def upload_file(credentials, local_path, key):
    """Upload a single local file to an exact OSS key."""
    _bucket(credentials).put_object_from_file(key, local_path)
