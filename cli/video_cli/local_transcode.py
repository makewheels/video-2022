"""Local FFmpeg transcoding pipeline for video-2022.

Transcodes a video on the local machine with FFmpeg and uploads the resulting
HLS (m3u8 + ts) to OSS, so the server never invokes paid cloud transcoding
(Aliyun MPS / cloud function). The server only registers the uploaded result.

Rendition rules mirror the server-side ResolutionPlanner (design §1):
display short side decides lanes; never upscale; no 480p lane.
"""
import json
import os
import shutil
import subprocess

# Resolution label -> output short side (pixels). Mirrors server ResolutionPlanner.
RESOLUTION_SHORT_SIDES = {"720p": 720, "1080p": 1080}

HDR_DYNAMIC_RANGES = ("HDR10", "HLG", "PQ", "DOLBY_VISION", "UNKNOWN")

TRANSFER_MAP = {"smpte2084": "PQ", "arib-std-b67": "HLG"}


def ensure_tools():
    """Raise if ffprobe/ffmpeg are not on PATH."""
    for tool in ("ffprobe", "ffmpeg"):
        if shutil.which(tool) is None:
            raise RuntimeError(f"{tool} not found in PATH. Install FFmpeg first (brew install ffmpeg).")


def _parse_fraction(value, default=None):
    if not value or value == "0/0":
        return default
    if "/" in value:
        num, den = value.split("/", 1)
        try:
            return round(int(num) / int(den), 3)
        except (ValueError, ZeroDivisionError):
            return default
    try:
        return round(float(value), 3)
    except ValueError:
        return default


def _detect_dynamic_range(video):
    """10-bit 不直接判 HDR；PQ/HLG/DOVI 识别，未知组合不默认 SDR。"""
    color_transfer = video.get("color_transfer")
    transfer = TRANSFER_MAP.get(color_transfer)
    side_data_types = [s.get("side_data_type", "") for s in video.get("side_data_list") or []]
    is_dovi = any("DOVI" in t for t in side_data_types)
    pix_fmt = video.get("pix_fmt")
    if is_dovi:
        return "DOLBY_VISION"
    if transfer == "PQ":
        return "HDR10"
    if transfer == "HLG":
        return "HLG"
    if pix_fmt and "10" in pix_fmt and color_transfer in (None, "", "unknown"):
        return "UNKNOWN"  # 不默认 SDR
    return "SDR"


def probe(filepath):
    """Probe a media file with ffprobe (media preservation fields included).

    Returns width/height (coded), displayWidth/displayHeight (rotation + SAR
    applied), duration_ms, codecs, bitrate_kbps, has_audio, frame_rate,
    frame_rate_mode, sar, rotation, pix_fmt, bit_depth, color info and
    dynamic_range.
    """
    cmd = ["ffprobe", "-v", "error", "-print_format", "json",
           "-show_format", "-show_streams", filepath]
    out = subprocess.run(cmd, capture_output=True, text=True, check=True).stdout
    data = json.loads(out)
    streams = data.get("streams", [])
    fmt = data.get("format", {})
    video = next((s for s in streams if s.get("codec_type") == "video"), None)
    audio_streams = [s for s in streams if s.get("codec_type") == "audio"]
    if video is None:
        raise RuntimeError("No video stream found in file")

    width, height = int(video["width"]), int(video["height"])
    rotation = 0
    for side in video.get("side_data_list") or []:
        if side.get("side_data_type") == "Display Matrix":
            rotation = ((int(float(side.get("rotation", 0))) % 360) + 360) % 360
    display_width, display_height = width, height
    if rotation in (90, 270):
        display_width, display_height = height, width
    sar = video.get("sample_aspect_ratio") or "1:1"
    try:
        sar_num, sar_den = (int(x) for x in sar.split(":"))
        if (sar_num, sar_den) != (1, 1) and sar_num > 0 and sar_den > 0:
            display_width = round(display_width * sar_num / sar_den)
    except ValueError:
        pass

    duration = float(fmt.get("duration") or video.get("duration") or 0)
    bit_rate = fmt.get("bit_rate") or video.get("bit_rate")

    dynamic_range = _detect_dynamic_range(video)

    pix_fmt = video.get("pix_fmt")
    color_transfer = video.get("color_transfer")

    avg_fps = video.get("avg_frame_rate")
    r_fps = video.get("r_frame_rate")
    frame_rate_mode = "VFR" if avg_fps and r_fps and avg_fps != r_fps else "CFR"

    return {
        "width": width,
        "height": height,
        "display_width": display_width,
        "display_height": display_height,
        "duration_ms": int(duration * 1000),
        "video_codec": video.get("codec_name"),
        "audio_codec": audio_streams[0].get("codec_name") if audio_streams else None,
        "bitrate_kbps": int(int(bit_rate) / 1000) if bit_rate else None,
        "has_audio": bool(audio_streams),
        "audio_track_count": len(audio_streams),
        "frame_rate": _parse_fraction(avg_fps),
        "frame_rate_mode": frame_rate_mode,
        "sar": sar,
        "rotation": rotation,
        "pix_fmt": pix_fmt,
        "bit_depth": 10 if (pix_fmt and "10" in pix_fmt) else 8,
        "color_primaries": video.get("color_primaries"),
        "color_transfer": color_transfer,
        "color_space": video.get("color_space"),
        "color_range": video.get("color_range"),
        "dynamic_range": dynamic_range,
    }


def target_resolutions(display_width, display_height):
    """Renditions to produce, mirroring server ResolutionPlanner.planResolutions.

    Display short side D: D >= 1080 -> 720p + 1080p; otherwise a single 720p
    lane (sub-720p sources keep their size under the 720p label, no upscale,
    no 480p lane).
    """
    short_side = min(display_width, display_height)
    if short_side >= 1080:
        return ["720p", "1080p"]
    return ["720p"]


def output_size(display_width, display_height, resolution):
    """Output size: short side capped to the lane target, ratio kept, no upscale,
    even-aligned (max 1 pixel adjustment per side)."""
    short_side = min(display_width, display_height)
    target = RESOLUTION_SHORT_SIDES.get(resolution)
    if target is None:
        raise ValueError(f"未知档位 {resolution}")
    w, h = display_width, display_height
    if short_side > target:
        if display_width >= display_height:
            h = target
            w = round(display_width * target / display_height)
        else:
            w = target
            h = round(display_height * target / display_width)
    return w - (w % 2), h - (h % 2)


def transcode_hls(input_path, out_dir, transcode_id, out_size, info=None):
    """Produce {transcode_id}.m3u8 + segments inside out_dir.

    ffmpeg runs with cwd=out_dir and relative names, so the m3u8 references
    segments by bare filename — exactly what the server's M3u8Util parser and
    OSS object listing expect.

    Media preservation: frame rate kept (no -r), rotation applied by decoder,
    color metadata re-tagged when present, all audio tracks mapped with
    channels/language preserved, no audio -> no silent track.
    """
    m3u8_name = f"{transcode_id}.m3u8"
    seg_name = f"{transcode_id}-%05d.ts"
    info = info or {}

    out_width, out_height = out_size
    cmd = ["ffmpeg", "-y", "-i", input_path]
    if (out_width, out_height) != (info.get("display_width"), info.get("display_height")):
        cmd += ["-vf", f"scale={out_width}:{out_height}"]

    dynamic_range = info.get("dynamic_range", "SDR")
    is_hdr = dynamic_range in HDR_DYNAMIC_RANGES
    if is_hdr:
        # HDR 原片本地同样不静默转 SDR：用 libx265 Main10 保留 10-bit 与色彩 tag；
        # 本机缺能力必须清楚报错，不自动改用收费云端
        cmd += ["-c:v", "libx265", "-profile:v", "main10", "-pix_fmt", "yuv420p10le",
                "-preset", "veryfast", "-crf", "26"]
        for value, flag in ((info.get("color_primaries"), "-color_primaries"),
                            (info.get("color_transfer"), "-color_trc"),
                            (info.get("color_space"), "-colorspace")):
            if value and value != "unknown":
                cmd += [flag, value]
        if not info.get("color_primaries"):
            cmd += ["-color_primaries", "bt2020"]
    else:
        cmd += ["-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
                "-pix_fmt", "yuv420p"]

    # 全部音轨映射（多音轨/语言/声道保留）；无音轨时映射为空，不造静音轨
    cmd += ["-map", "0:v:0", "-map", "0:a?"]
    if info.get("has_audio"):
        cmd += ["-c:a", "aac", "-b:a", "128k"]
    else:
        cmd += ["-c:a", "copy"]

    cmd += ["-hls_time", "6", "-hls_playlist_type", "vod", "-hls_list_size", "0",
            "-hls_segment_filename", seg_name, "-f", "hls", m3u8_name]
    result = subprocess.run(cmd, cwd=out_dir, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(
            f"local transcode failed for {m3u8_name}: {result.stderr[-500:]}")
    return m3u8_name


def extract_cover(input_path, out_path, at_seconds):
    """Grab a single frame as the cover image (SDR jpg; HDR tone-maps when available)."""
    cmd = ["ffmpeg", "-y", "-ss", str(at_seconds), "-i", input_path, "-frames:v", "1"]
    info = None
    try:
        info = probe(input_path)
    except Exception:  # noqa: BLE001 封面探测失败按 SDR 处理
        pass
    if info and info.get("dynamic_range") in HDR_DYNAMIC_RANGES:
        # 先试 tone-map 滤镜链，不可用再退回原样截帧（图片色彩可能偏差，明确打印警告）
        tm = ("zscale=t=linear:npl=100,format=gbrpf32le,zscale=p=bt709,"
              "tonemap=tonemap=hable:desat=0,zscale=t=bt709:m=bt709:r=tv,format=yuv420p")
        tm_cmd = cmd + ["-vf", tm, "-q:v", "2", out_path]
        if subprocess.run(tm_cmd, capture_output=True).returncode == 0 and os.path.exists(out_path):
            return
        print("Warning: HDR cover tone-map unavailable; output keeps source transfer tags")
    cmd += ["-q:v", "2", out_path]
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
