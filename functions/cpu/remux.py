"""TRANSCODE remux：无需重编码的源（H.264/AAC 合规、码率与尺寸合规、SDR 4:2:0）
直接 -c copy 切 HLS TS。帧率、色彩、方向、音轨全部原样保留。"""
import os
import subprocess

from common import media


def handle_remux(input_path: str, work_dir: str, oss, body: dict) -> dict:
    policy = body.get("mediaPolicy") or {}
    if policy.get("reencode"):
        raise RuntimeError("remux 任务不允许 reencode=true，应由 GPU 函数处理")
    if policy.get("segmentType", "ts") != "ts":
        raise RuntimeError("remux 仅支持 TS 封装")

    playlist_key = body.get("playlistKey")
    if not playlist_key:
        raise RuntimeError("缺少 playlistKey")
    playlist_name = os.path.basename(playlist_key)
    segment_pattern = playlist_name.replace(".m3u8", "") + "-%05d.ts"
    hls_dir = os.path.join(work_dir, "hls")
    os.makedirs(hls_dir, exist_ok=True)

    segment_seconds = int(policy.get("segmentSeconds", 6))
    cmd = ["ffmpeg", "-y", "-i", input_path]
    # 保留全部音轨（含多音轨/语言 metadata）；无音轨自然跳过
    cmd += ["-map", "0", "-c", "copy"]
    cmd += ["-hls_time", str(segment_seconds), "-hls_playlist_type", "vod",
            "-hls_list_size", "0", "-hls_segment_filename",
            os.path.join(hls_dir, segment_pattern), "-f", "hls",
            os.path.join(hls_dir, playlist_name)]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=3600)
    if result.returncode != 0:
        raise RuntimeError(f"remux 失败: {result.stderr[-500:]}")

    from common.job import upload_all_files
    objects = upload_all_files(oss, hls_dir, body["outputDir"])

    media_info = media.build_media_info(input_path)
    vstream = media.probe_stream(input_path, "video")
    astream = media.probe_stream(input_path, "audio")
    return {
        "playlistKey": playlist_key,
        "objects": objects,
        "width": media_info["displayWidth"],
        "height": media_info["displayHeight"],
        "durationMs": media_info["durationMs"],
        "codec": media_info["videoCodec"],
        "profile": vstream.get("profile"),
        "pixelFormat": media_info["pixFmt"],
        "bitDepth": media_info["bitDepth"],
        "colorPrimaries": media_info["colorPrimaries"],
        "colorTransfer": media_info["colorTransfer"],
        "colorSpace": media_info["colorSpace"],
        "colorRange": media_info["colorRange"],
        "dynamicRange": media_info["dynamicRange"],
        "frameRate": media_info["frameRate"],
        "frameRateMode": media_info["frameRateMode"],
        "hlsCodecs": media.build_hls_codecs(vstream, astream),
        "audioTracks": media_info["audioTracks"],
        "mode": "remux",
    }
