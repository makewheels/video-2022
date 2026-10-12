"""TRANSCODE（GPU）：NVENC 重编码。mediaPolicy 驱动，帧率一律 preserve，
色彩元数据透传，HDR 输出 HEVC Main10 + fMP4，SDR 输出 H.264 + TS。"""
import os
import re
import subprocess

from common import media
from gpu.hdr_metadata import preserve_static_hdr, static_metadata_equal


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
    return args


def _video_encoder_args(policy: dict, is_hdr: bool) -> list:
    """NVENC 编码器参数。本地测试通过 monkeypatch 本函数替换为软件编码器。"""
    args = ["-c:v", policy["videoEncoder"]]
    if policy["videoEncoder"] == "hevc_nvenc":
        # HEVC Main10；fMP4 时打 hvc1 tag 兼容苹果生态
        args += ["-profile:v", "main10", "-pix_fmt", "p010le"]
        if policy.get("segmentType") == "fmp4":
            args += ["-tag:v", "hvc1"]
    else:
        args += ["-pix_fmt", "yuv420p"]
    args += ["-preset", "p4", "-rc", "vbr", "-forced-idr", "1"]
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

    source_info = media.build_media_info(input_path)
    if source_info.get("bitDepth", 8) > 10:
        raise media.UnsupportedMediaError("当前 GPU 不支持保留超过 10-bit 的原片")
    if source_info["dynamicRange"] in ("DOLBY_VISION", "HDR10_PLUS", "UNKNOWN"):
        raise media.UnsupportedMediaError("当前 GPU 无法验证保留该格式的动态 HDR 元数据")
    source_video = media.probe_stream(input_path, "video")
    if source_video is None:
        raise RuntimeError("没有视频流")

    cmd = ["ffmpeg", "-y", "-i", input_path]
    # 视频链：CPU 解码 → 缩放（比例已由服务端算好，无裁剪/拉伸）→ NVENC 编码
    cmd += ["-vf", f"scale={out_width}:{out_height},setsar=1",
            "-fps_mode", "passthrough", "-force_key_frames", f"expr:gte(t,n_forced*{segment_seconds})"]
    cmd += _video_encoder_args(policy, is_hdr)
    cmd += ["-b:v", str(target), "-maxrate", str(maxrate), "-bufsize", str(bufsize)]
    # 帧率 preserve：不设置 -r / fps filter；方向由输入 display matrix 自带
    cmd += _color_args(policy, is_hdr)

    # 音频：encode 转 AAC，保留全部音轨/声道/语言 metadata；无音轨时映射为空不造静音轨
    audio_policy = policy.get("audioPolicy") or {}
    if audio_policy.get("preserveTracks", True) is not True:
        raise RuntimeError("audioPolicy.preserveTracks=false 与媒体保留要求冲突")
    if audio_policy.get("mode", "encode") == "encode":
        cmd += ["-map", "0:v:0", "-map", "0:a?", "-c:a", "aac", "-b:a", "128k"]
    else:
        cmd += ["-map", "0:v:0", "-map", "0:a?", "-c:a", "copy"]

    encoded = os.path.join(work_dir, "encoded.mp4")
    cmd += ["-f", "mp4", encoded]

    result = subprocess.run(cmd, capture_output=True, text=True, check=False, timeout=3600)
    if result.returncode != 0:
        raise RuntimeError(f"NVENC 编码失败: {result.stderr[-800:]}")

    encoded = preserve_static_hdr(encoded, os.path.join(work_dir, "encoded-hdr.mp4"),
                                  source_info.get("hdrSideData") or {})
    package = ["ffmpeg", "-y", "-i", encoded, "-map", "0:v:0", "-map", "0:a?", "-c", "copy",
               "-hls_time", str(segment_seconds), "-hls_playlist_type", "vod", "-hls_list_size", "0"]
    if segment_type == "fmp4":
        package += ["-tag:v", "hvc1", "-hls_segment_type", "fmp4"]
    package += ["-hls_segment_filename", os.path.join(hls_dir, segment_pattern),
                "-f", "hls", os.path.join(hls_dir, playlist_name)]
    result = subprocess.run(package, capture_output=True, text=True, check=False, timeout=3600)
    if result.returncode != 0:
        raise RuntimeError("HLS 封装失败: " + result.stderr[-500:])

    from common.job import upload_all_files
    objects = []

    # 输出实测信息：ffprobe 输出 playlist（实际封装后的流）
    playlist_path = os.path.join(hls_dir, playlist_name)
    out_video = media.probe_stream(playlist_path, "video")
    out_audio = media.probe_stream(playlist_path, "audio")
    fmt = media.run_ffprobe(playlist_path).get("format", {})
    duration_ms = int(float(fmt.get("duration") or 0) * 1000)

    output_info = media.build_media_info(playlist_path)
    if output_info["dynamicRange"] != source_info["dynamicRange"]:
        raise media.UnsupportedMediaError("输出动态范围与原片不同")
    for field in ("colorPrimaries", "colorTransfer", "colorSpace", "colorRange"):
        if source_info.get(field) and source_info[field] != output_info.get(field):
            raise media.UnsupportedMediaError("输出色彩信息未保留: " + field)
    if output_info["audioTrackCount"] != source_info["audioTrackCount"]:
        raise media.UnsupportedMediaError("输出音轨数量不符")
    if not static_metadata_equal(source_info.get("hdrSideData"), output_info.get("hdrSideData")):
        raise media.UnsupportedMediaError("输出 HDR 静态元数据未完整保留")
    objects = upload_all_files(oss, hls_dir, body["outputDir"])
    # 实际平均/峰值码率：分片大小与 EXTINF 时长逐片计算
    segment_objects = [o for o in objects if o["type"] == "segment"]
    extinf_seconds = _parse_extinf_seconds(playlist_path)
    max_bitrate = 0
    total_size = sum(o["size"] for o in segment_objects)
    for obj, seconds in zip(segment_objects, extinf_seconds):
        if seconds > 0:
            max_bitrate = max(max_bitrate, int(obj["size"] * 8 / seconds))
    average_bitrate = int(total_size * 8 / (duration_ms / 1000)) if duration_ms else 0

    return {
        "playlistKey": playlist_key,
        "objects": objects,
        "width": output_info["displayWidth"],
        "height": output_info["displayHeight"],
        "durationMs": duration_ms,
        "codec": out_video.get("codec_name") if out_video else policy["videoCodec"],
        "profile": out_video.get("profile"),
        "pixelFormat": out_video.get("pix_fmt"),
        "bitDepth": 10 if out_video and "10" in (out_video.get("pix_fmt") or "") else 8,
        "colorPrimaries": output_info.get("colorPrimaries"),
        "colorTransfer": output_info.get("colorTransfer"),
        "colorSpace": output_info.get("colorSpace"),
        "colorRange": output_info.get("colorRange"),
        "dynamicRange": output_info.get("dynamicRange"),
        "frameRate": out_video.get("avg_frame_rate") if out_video else None,
        "frameRateMode": media.build_media_info(input_path).get("frameRateMode", "CFR"),
        "hlsCodecs": media.build_hls_codecs(out_video, out_audio),
        "audioTracks": output_info["audioTracks"],
        "averageBitrateBps": average_bitrate,
        "maxBitrateBps": max_bitrate or maxrate,
        "mode": "nvenc",
    }
