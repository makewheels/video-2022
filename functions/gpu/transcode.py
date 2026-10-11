"""TRANSCODE（GPU）：NVENC 重编码。mediaPolicy 驱动，帧率一律 preserve，
色彩元数据透传，HDR 输出 HEVC Main10 + fMP4，SDR 输出 H.264 + TS。"""
import os
import re
import subprocess

from common import media


def _color_args(policy: dict, is_hdr: bool) -> list:
    args = []
    mapping = {
        "colorPrimaries": "-color_primaries",
        "colorTransfer": "-color_trc",
        "colorSpace": "-colorspace",
        "colorRange": "-color_range",
    }
    for key, flag in mapping.items():
        value = policy.get(key)
        if value and value != "unknown":
            args += [flag, value]
    # HDR 必须显式 bt2020 primaries（ffprobe 缺失时兜底），避免输出退化为 bt709 标签
    if is_hdr and not policy.get("colorPrimaries"):
        args += ["-color_primaries", "bt2020"]
    return args


def _hdr_bsf_args(policy: dict) -> list:
    """HDR10 静态元数据（maxCLL/maxFALL）尽量透传；不可用则跳过。"""
    side_data = policy.get("hdrSideData") or {}
    content_light = side_data.get("Content light level metadata") or {}
    params = []
    if content_light.get("max_content") is not None:
        params.append(f"max_cll={content_light['max_content']}")
    if content_light.get("max_average") is not None:
        params.append(f"max_fall={content_light['max_average']}")
    if not params:
        return []
    return ["-bsf:v", "hevc_metadata=" + ":".join(params)]


def _video_encoder_args(policy: dict, is_hdr: bool) -> list:
    """NVENC 编码器参数。本地测试通过 monkeypatch 本函数替换为软件编码器。"""
    args = ["-c:v", policy["videoEncoder"]]
    if is_hdr:
        # HEVC Main10；fMP4 时打 hvc1 tag 兼容苹果生态
        args += ["-profile:v", "main10", "-pix_fmt", "p010le"]
        if policy.get("segmentType") == "fmp4":
            args += ["-tag:v", "hvc1"]
    args += ["-preset", "p4"]
    return args


def _parse_extinf_seconds(m3u8_path: str) -> list:
    extinf = re.compile(r"#EXTINF:([\d.]+)")
    seconds = []
    with open(m3u8_path, encoding="utf-8") as f:
        for line in f:
            m = extinf.match(line.strip())
            if m:
                seconds.append(float(m.group(1)))
    return seconds


def handle_transcode(input_path: str, work_dir: str, oss, body: dict) -> dict:
    policy = body.get("mediaPolicy") or {}
    if not policy.get("reencode"):
        raise RuntimeError("GPU 函数只处理 reencode=true 任务")
    if policy.get("videoEncoder") not in ("h264_nvenc", "hevc_nvenc"):
        raise RuntimeError(f"未知 videoEncoder = {policy.get('videoEncoder')}")

    playlist_key = body.get("playlistKey")
    if not playlist_key:
        raise RuntimeError("缺少 playlistKey")
    playlist_name = os.path.basename(playlist_key)
    segment_type = policy.get("segmentType", "ts")
    ext = "m4s" if segment_type == "fmp4" else "ts"
    segment_pattern = playlist_name.replace(".m3u8", "") + "-%05d." + ext
    hls_dir = os.path.join(work_dir, "hls")
    os.makedirs(hls_dir, exist_ok=True)

    is_hdr = policy.get("dynamicRange") in ("HDR10", "HLG", "PQ", "DOLBY_VISION", "UNKNOWN")
    out_width = int(policy["outputWidth"])
    out_height = int(policy["outputHeight"])
    target = policy["targetBitrateBps"]
    maxrate = policy["maxBitrateBps"]
    bufsize = policy["bufferBits"]
    segment_seconds = int(policy.get("segmentSeconds", 6))

    source_video = media.probe_stream(input_path, "video")
    if source_video is None:
        raise RuntimeError("没有视频流")

    cmd = ["ffmpeg", "-y", "-i", input_path]
    # 视频链：CPU 解码 → 缩放（比例已由服务端算好，无裁剪/拉伸）→ NVENC 编码
    if (out_width, out_height) != (int(source_video["width"]), int(source_video["height"])):
        cmd += ["-vf", f"scale={out_width}:{out_height}"]
    cmd += _video_encoder_args(policy, is_hdr)
    cmd += ["-b:v", str(target), "-maxrate", str(maxrate), "-bufsize", str(bufsize)]
    # 帧率 preserve：不设置 -r / fps filter；方向由输入 display matrix 自带
    cmd += _color_args(policy, is_hdr) + _hdr_bsf_args(policy)

    # 音频：encode 转 AAC，保留全部音轨/声道/语言 metadata；无音轨时映射为空不造静音轨
    audio_policy = policy.get("audioPolicy") or {}
    if audio_policy.get("preserveTracks", True) is not True:
        raise RuntimeError("audioPolicy.preserveTracks=false 与媒体保留要求冲突")
    if audio_policy.get("mode", "encode") == "encode":
        cmd += ["-map", "0:v:0", "-map", "0:a?", "-c:a", "aac", "-b:a", "128k"]
    else:
        cmd += ["-map", "0:v:0", "-map", "0:a?", "-c:a", "copy"]

    cmd += ["-hls_time", str(segment_seconds), "-hls_playlist_type", "vod",
            "-hls_list_size", "0"]
    if segment_type == "fmp4":
        cmd += ["-hls_segment_type", "fmp4"]
    cmd += ["-hls_segment_filename", os.path.join(hls_dir, segment_pattern),
            "-f", "hls", os.path.join(hls_dir, playlist_name)]

    result = subprocess.run(cmd, capture_output=True, text=True, timeout=3600)
    if result.returncode != 0:
        raise RuntimeError(f"NVENC 编码失败: {result.stderr[-800:]}")

    from common.job import upload_all_files
    objects = upload_all_files(oss, hls_dir, body["outputDir"])

    # 输出实测信息：ffprobe 输出 playlist（实际封装后的流）
    playlist_path = os.path.join(hls_dir, playlist_name)
    out_video = media.probe_stream(playlist_path, "video")
    out_audio = media.probe_stream(playlist_path, "audio")
    fmt = media.run_ffprobe(playlist_path).get("format", {})
    duration_ms = int(float(fmt.get("duration") or 0) * 1000)

    # 实际平均/峰值码率：分片大小与 EXTINF 时长逐片计算
    segment_objects = [o for o in objects if o["type"] == "segment"]
    extinf_seconds = _parse_extinf_seconds(playlist_path)
    max_bitrate = 0
    total_size = sum(o["size"] for o in segment_objects)
    for obj, seconds in zip(segment_objects, extinf_seconds):
        if seconds > 0:
            max_bitrate = max(max_bitrate, int(obj["size"] * 8 / seconds))
    average_bitrate = int(total_size * 8 / (duration_ms / 1000)) if duration_ms else out_bitrate

    return {
        "playlistKey": playlist_key,
        "objects": objects,
        "width": out_width,
        "height": out_height,
        "durationMs": duration_ms,
        "codec": out_video.get("codec_name") if out_video else policy["videoCodec"],
        "profile": out_video.get("profile"),
        "pixelFormat": out_video.get("pix_fmt"),
        "bitDepth": 10 if out_video and "10" in (out_video.get("pix_fmt") or "") else 8,
        "colorPrimaries": policy.get("colorPrimaries"),
        "colorTransfer": policy.get("colorTransfer"),
        "colorSpace": policy.get("colorSpace"),
        "colorRange": policy.get("colorRange"),
        "dynamicRange": policy.get("dynamicRange"),
        "frameRate": out_video.get("avg_frame_rate") if out_video else None,
        "frameRateMode": media.build_media_info(input_path).get("frameRateMode", "CFR"),
        "hlsCodecs": media.build_hls_codecs(out_video, out_audio),
        "audioTracks": _audio_tracks(out_audio),
        "averageBitrateBps": average_bitrate,
        "maxBitrateBps": max_bitrate or maxrate,
        "mode": "nvenc",
    }


def _audio_tracks(audio_stream: dict) -> dict:
    if audio_stream is None:
        return {"tracks": []}
    return {"tracks": [{
        "index": audio_stream.get("index"),
        "codec": audio_stream.get("codec_name"),
        "channels": audio_stream.get("channels"),
        "language": (audio_stream.get("tags") or {}).get("language"),
    }]}
