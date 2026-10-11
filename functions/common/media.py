"""ffprobe 解析与媒体信息 manifest 构建。CPU 函数（PROBE）与 GPU 函数共用。"""
import json
import subprocess

# 已知的 HDR 传递函数映射；未知组合一律按 UNKNOWN 处理（调用方不得默认 SDR）
TRANSFER_MAP = {
    "smpte2084": "PQ",
    "arib-std-b67": "HLG",
}


def run_ffprobe(input_path: str) -> dict:
    cmd = ["ffprobe", "-v", "error", "-show_format", "-show_streams", "-of", "json", input_path]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
    if result.returncode != 0:
        raise RuntimeError(f"ffprobe 失败: {result.stderr[-500:]}")
    return json.loads(result.stdout)


def _rotation_of(video_stream: dict) -> int:
    for side in video_stream.get("side_data_list", []) or []:
        if side.get("side_data_type") == "Display Matrix":
            rotation = int(float(side.get("rotation", 0)))
            return ((rotation % 360) + 360) % 360
    return 0


def _sar_num_den(video_stream: dict) -> tuple:
    sar = video_stream.get("sample_aspect_ratio", "1:1")
    try:
        num, den = sar.split(":")
        return int(num), int(den)
    except (ValueError, AttributeError):
        return 1, 1


def _detect_dynamic_range(video_stream: dict, color_transfer: str) -> tuple:
    """返回 (dynamicRange, hdrSideData)。未知不默认 SDR。"""
    side_data_list = video_stream.get("side_data_list", []) or []
    hdr_side_data = {}
    is_dovi = False
    for side in side_data_list:
        t = side.get("side_data_type", "")
        if "DOVI" in t or "dovi" in t:
            is_dovi = True
        if t in ("Mastering display metadata", "Content light level metadata"):
            hdr_side_data[t] = side

    transfer = TRANSFER_MAP.get(color_transfer)
    if is_dovi:
        return "DOLBY_VISION", hdr_side_data
    if transfer == "PQ":
        return "HDR10", hdr_side_data
    if transfer == "HLG":
        return "HLG", hdr_side_data
    # 10-bit + BT.2020 但 transfer 未知：按 UNKNOWN 处理（不默认 SDR）
    if "10" in (video_stream.get("pix_fmt") or "") and color_transfer in (None, "", "unknown"):
        return "UNKNOWN", hdr_side_data
    return "SDR", hdr_side_data


def build_media_info(input_path: str) -> dict:
    """从 ffprobe 输出构建协议 v1 的 mediaInfo。"""
    data = run_ffprobe(input_path)
    streams = data.get("streams", [])
    fmt = data.get("format", {})
    video = next((s for s in streams if s.get("codec_type") == "video"), None)
    if video is None:
        raise RuntimeError("没有视频流")
    audio_streams = [s for s in streams if s.get("codec_type") == "audio"]

    width = int(video["width"])
    height = int(video["height"])
    rotation = _rotation_of(video_stream=video)
    disp_w, disp_h = width, height
    if rotation in (90, 270):
        disp_w, disp_h = height, width
    sar_num, sar_den = _sar_num_den(video)
    if sar_num > 0 and sar_den > 0 and (sar_num, sar_den) != (1, 1):
        disp_w = round(disp_w * sar_num / sar_den)

    duration_ms = int(float(fmt.get("duration") or video.get("duration") or 0) * 1000)
    bit_rate = fmt.get("bit_rate") or video.get("bit_rate")
    bitrate_bps = int(float(bit_rate)) if bit_rate else 0

    color_primaries = video.get("color_primaries") or None
    color_transfer = video.get("color_transfer") or None
    color_space = video.get("color_space") or None
    color_range = video.get("color_range") or None
    pix_fmt = video.get("pix_fmt")
    bit_depth = 10 if (pix_fmt and "10" in pix_fmt) else (8 if pix_fmt else None)
    dynamic_range, hdr_side_data = _detect_dynamic_range(video, color_transfer or "")

    frame_rate = video.get("avg_frame_rate") or video.get("r_frame_rate")
    frame_rate_mode = "CFR"
    if (video.get("avg_frame_rate") and video.get("r_frame_rate")
            and video.get("avg_frame_rate") != video.get("r_frame_rate")):
        frame_rate_mode = "VFR"

    audio_tracks = [
        {
            "index": s.get("index"),
            "codec": s.get("codec_name"),
            "channels": s.get("channels"),
            "language": (s.get("tags") or {}).get("language"),
            "title": (s.get("tags") or {}).get("title"),
        }
        for s in audio_streams
    ]

    return {
        "width": width,
        "height": height,
        "displayWidth": disp_w,
        "displayHeight": disp_h,
        "durationMs": duration_ms,
        "videoCodec": video.get("codec_name"),
        "audioCodec": audio_streams[0].get("codec_name") if audio_streams else None,
        "hasAudio": bool(audio_streams),
        "bitrateBps": bitrate_bps,
        "frameRate": frame_rate,
        "frameRateMode": frame_rate_mode,
        "sar": video.get("sample_aspect_ratio") or "1:1",
        "rotation": rotation,
        "pixFmt": pix_fmt,
        "bitDepth": bit_depth,
        "colorPrimaries": color_primaries,
        "colorTransfer": color_transfer,
        "colorSpace": color_space,
        "colorRange": color_range,
        "dynamicRange": dynamic_range,
        "hdrSideData": hdr_side_data or None,
        "audioTrackCount": len(audio_tracks),
        "audioTracks": {"tracks": audio_tracks},
    }


def _avc_codec_string(video_stream: dict) -> str:
    profile_name = (video_stream.get("profile") or "").lower()
    if "baseline" in profile_name:
        profile_idc = "42"
    elif "main" in profile_name and "10" in profile_name:
        profile_idc = "2d"  # Main10 (High10) H.264 少见，仅兜底
    elif "main" in profile_name:
        profile_idc = "4d"
    else:
        profile_idc = "64"  # High 及未知按 High
    constraint = "00"
    try:
        level = int(video_stream.get("level") or 41)
        level_hex = f"{level:02x}"
    except (TypeError, ValueError):
        level_hex = "29"
    return f"avc1.{profile_idc}{constraint}{level_hex}"


def _hevc_codec_string(video_stream: dict) -> str:
    profile_name = (video_stream.get("profile") or "").lower()
    profile_idc = 2 if "main 10" in profile_name or "main10" in profile_name else 1
    try:
        level = int(video_stream.get("level") or 93)
        level_token = f"L{level}"
    except (TypeError, ValueError):
        level_token = "L93"
    return f"hvc1.1.6.{level_token}.B0"


def build_hls_codecs(video_stream: dict, audio_stream: dict) -> str:
    """HLS CODECS 属性值。无音轨时只给视频 codec，不造假音频。"""
    if video_stream is None:
        raise RuntimeError("没有视频流")
    vcodec = video_stream.get("codec_name")
    if vcodec == "h264":
        parts = [_avc_codec_string(video_stream)]
    elif vcodec in ("hevc", "h265"):
        parts = [_hevc_codec_string(video_stream)]
    else:
        # 非受支持编码不编造 codec 值，返回 None 让主播放列表省略 CODECS
        parts = []
    if audio_stream is not None and audio_stream.get("codec_name") == "aac":
        parts.append("mp4a.40.2")
    return ",".join(parts) if parts else None


def probe_stream(input_path: str, stream_type: str) -> dict:
    data = run_ffprobe(input_path)
    streams = data.get("streams", [])
    return next((s for s in streams if s.get("codec_type") == stream_type), None)
