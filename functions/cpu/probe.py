"""PROBE：ffprobe 探测，返回完整媒体信息 manifest。"""
import os

from common import media
from common.protocol import SCHEMA_VERSION


def handle_probe(input_path: str, work_dir: str, oss, body: dict) -> dict:
    media_info = media.build_media_info(input_path)
    return {
        "schemaVersion": SCHEMA_VERSION,
        "taskId": body["taskId"],
        "attemptId": body["attemptId"],
        "operation": "PROBE",
        "mediaInfo": media_info,
    }
