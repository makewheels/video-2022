"""Read codec configuration from an fMP4 init segment when FFmpeg omits CODECS."""
import json
import os
import re
import subprocess


def from_master_or_init(master_path, init_path):
    with open(master_path, encoding="utf-8") as master:
        match = re.search(r'CODECS="([^"]+)"', master.read())
    os.unlink(master_path)
    if match:
        return match.group(1)
    return from_init(init_path) if os.path.exists(init_path) else None


def _extradata(stream):
    parts = []
    for line in (stream.get("extradata") or "").splitlines():
        if ":" not in line:
            continue
        try:
            parts.append(bytes.fromhex(line.split(":", 1)[1].strip().split("  ", 1)[0]))
        except ValueError:
            return b""
    return b"".join(parts)


def from_init(path):
    result = subprocess.run([
        "ffprobe", "-v", "error", "-show_streams", "-show_data", "-of", "json", path,
    ], capture_output=True, text=True, check=True)
    streams = json.loads(result.stdout).get("streams", [])
    video = next((s for s in streams if s.get("codec_type") == "video"), {})
    data = _extradata(video)
    if video.get("codec_name") != "hevc" or data[:1] != b"\x01" or len(data) < 13:
        return None
    ptl = data[1:13]
    space = ["", "A", "B", "C"][ptl[0] >> 6]
    profile = ptl[0] & 31
    compatibility = int(f"{int.from_bytes(ptl[1:5], 'big'):032b}"[::-1], 2)
    tier = "H" if ptl[0] & 32 else "L"
    constraints = ".".join(f"{v:02X}" for v in ptl[5:11].rstrip(b"\x00"))
    codec = f"hvc1.{space}{profile}.{compatibility:X}.{tier}{ptl[11]}"
    parts = [codec + ("." + constraints if constraints else "")]
    for audio in (s for s in streams if s.get("codec_type") == "audio"):
        config = _extradata(audio)
        if audio.get("codec_name") != "aac" or not config:
            return None
        object_type = config[0] >> 3
        if object_type == 31:
            if len(config) < 2:
                return None
            object_type = 32 + ((config[0] & 7) << 3) + (config[1] >> 5)
        if not object_type:
            return None
        audio_codec = f"mp4a.40.{object_type}"
        if audio_codec not in parts:
            parts.append(audio_codec)
    return ",".join(parts)
