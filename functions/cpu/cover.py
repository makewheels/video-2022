"""COVER：截帧封面。SDR 直接出 jpg；PQ/HLG 输入先 tone-map 为可显示 SDR 图片。"""
import os
import subprocess

from common import media


HDR_ZSCALE_FILTER = (
    "zscale=t=linear:npl=100,format=gbrpf32le,"
    "zscale=p=bt709,tonemap=tonemap=hable:desat=0,"
    "zscale=t=bt709:m=bt709:r=tv,format=yuv420p"
)

HDR_LIBPLACEBO_FILTER = (
    "libplacebo=colorspace=bt709:tonemap=hable:color_primaries=bt709:"
    "color_trc=bt709:peak_detect=false"
)


def _extract_frame(input_path: str, out_path: str, at_seconds: float,
                   hdr_filter: str) -> bool:
    cmd = ["ffmpeg", "-y", "-ss", str(at_seconds), "-i", input_path, "-frames:v", "1"]
    if hdr_filter:
        cmd += ["-vf", hdr_filter]
    cmd += ["-q:v", "2", out_path]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
    return result.returncode == 0 and os.path.exists(out_path)


def handle_cover(input_path: str, work_dir: str, oss, body: dict) -> dict:
    image_key = body.get("imageKey")
    if not image_key:
        raise RuntimeError("COVER 任务缺少 imageKey")
    image_format = body.get("imageFormat", "jpg")
    at_seconds = body.get("atSeconds", 0)
    ext = "jpg" if image_format == "jpg" else "png"
    out_path = os.path.join(work_dir, f"cover.{ext}")

    media_info = media.build_media_info(input_path)
    is_hdr = media_info.get("dynamicRange") in ("HDR10", "HLG", "PQ", "DOLBY_VISION", "UNKNOWN")

    ok = False
    if is_hdr:
        # HDR 原片必须 tone-map 成可显示图片：zscale 与 libplacebo 二选一，
        # 都不可用则明确失败（不静默输出错误颜色的图片）
        for hdr_filter in (HDR_ZSCALE_FILTER, HDR_LIBPLACEBO_FILTER):
            if _extract_frame(input_path, out_path, at_seconds, hdr_filter):
                ok = True
                break
        if not ok:
            raise RuntimeError(
                "HDR 封面 tone-map 失败：运行环境缺少 zscale/libplacebo 滤镜，"
                "请检查镜像 ffmpeg 编译特性")
    else:
        ok = _extract_frame(input_path, out_path, at_seconds, None)
    if not ok:
        raise RuntimeError("截帧失败")

    oss.upload_file(out_path, image_key)
    # 输出图片的实际尺寸
    probe = media.run_ffprobe(out_path)
    vstream = next((s for s in probe.get("streams", []) if s.get("codec_type") == "video"), {})
    return {
        "imageKey": image_key,
        "width": int(vstream.get("width", 0)),
        "height": int(vstream.get("height", 0)),
        "sourceDynamicRange": media_info.get("dynamicRange"),
    }
