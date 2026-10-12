"""协议 v1 公共库：请求校验、回调、manifest 构建。CPU/GPU 函数共用。"""
import hmac
import json
import os
from pathlib import PurePosixPath
from urllib import request as urlrequest
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse

SCHEMA_VERSION = 1

OPERATIONS = ("PROBE", "COVER", "TRANSCODE")

# outputDir 必须位于 videoId 的专属前缀下，禁止写到其它视频目录或原片目录
ALLOWED_OUTPUT_PREFIXES = ("videos/", "transcode/")


class ProtocolError(Exception):
    """请求不合法。"""


def validate_request(body: dict, invoke_secret: str, header_secret: str = "") -> dict:
    """校验任务请求：schema、密钥、必填字段与 outputDir 归属。失败抛 ProtocolError。"""
    if not isinstance(body, dict):
        raise ProtocolError("body 必须是 JSON 对象")
    if body.get("schemaVersion") != SCHEMA_VERSION:
        raise ProtocolError(f"schemaVersion 必须为 {SCHEMA_VERSION}")
    operation = body.get("operation")
    if operation not in OPERATIONS:
        raise ProtocolError(f"未知 operation = {operation}")

    required = ("taskId", "attemptId", "videoId", "bucket", "endpoint",
                "inputKey", "outputDir", "callbackUrl")
    missing = [k for k in required if not body.get(k)]
    if missing:
        raise ProtocolError(f"缺少必填字段: {missing}")

    for key in ("taskId", "attemptId", "videoId"):
        value = body[key]
        if not isinstance(value, str) or not value or any(c not in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-" for c in value):
            raise ProtocolError("任务身份格式非法")
    output_dir = body["outputDir"]
    path = PurePosixPath(output_dir)
    if (not output_dir.startswith("videos/") or ".." in path.parts
            or body["videoId"] not in path.parts or "raw" in path.parts
            or "\\" in output_dir or ":" in output_dir):
        raise ProtocolError("outputDir 不属于当前视频产物目录")
    input_path = PurePosixPath(body["inputKey"])
    if (not body["inputKey"].startswith("videos/") or ".." in input_path.parts
            or body["videoId"] not in input_path.parts or "raw" not in input_path.parts):
        raise ProtocolError("inputKey 不属于当前视频原片")
    video_root = input_path.parts[:input_path.parts.index(body["videoId"]) + 1]
    if path.parts[:len(video_root)] != video_root:
        raise ProtocolError("输入和输出的视频目录不同")
    for key in ("playlistKey", "imageKey"):
        value = body.get(key)
        if value is not None and (str(PurePosixPath(value).parent) != output_dir
                                  or ".." in PurePosixPath(value).parts):
            raise ProtocolError("产物路径不属于 outputDir")
    allowed_host = os.environ.get("CALLBACK_ALLOWED_HOST", "oneclick.video")
    callback = urlparse(body["callbackUrl"])
    if (callback.scheme != "https" or callback.hostname != allowed_host
            or callback.path != "/transcode/cloudFunctionCallback" or callback.query
            or callback.username or callback.password):
        raise ProtocolError("回调地址不在配置范围内")
    if not invoke_secret or not hmac.compare_digest(header_secret, invoke_secret):
        raise ProtocolError("调用密钥校验失败")
    return body


def send_callback(callback_url: str, payload: dict, callback_secret: str,
                  timeout_seconds: int = 30) -> bool:
    """回调后端。网络失败重试 2 次；最终失败写日志由超时恢复兜底。"""
    data = json.dumps(payload).encode("utf-8")
    last_error = None
    for attempt in range(3):
        try:
            req = urlrequest.Request(callback_url, data=data, method="POST")
            req.add_header("Content-Type", "application/json")
            if callback_secret:
                req.add_header("X-Callback-Secret", callback_secret)
            with urlrequest.urlopen(req, timeout=timeout_seconds) as resp:
                if 200 <= resp.status < 300:
                    return True
                last_error = f"HTTP {resp.status}"
        except (HTTPError, URLError, TimeoutError) as e:
            last_error = str(e)
    print(f"[protocol] 回调失败 callbackUrl 已脱敏, error = {last_error}")
    return False


def build_success_manifest(task_id: str, attempt_id: str, operation: str, **extra) -> dict:
    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "taskId": task_id,
        "attemptId": attempt_id,
        "operation": operation,
        "status": "SUCCEEDED",
    }
    manifest.update(extra)
    return manifest


def build_failure_payload(task_id: str, attempt_id: str, operation: str,
                          error_message: str) -> dict:
    # 只回传任务身份与脱敏错误，不回传请求参数与内部路径
    message = (error_message or "unknown error").strip()
    message = message.replace("\n", " ")[:500]
    return {
        "schemaVersion": SCHEMA_VERSION,
        "taskId": task_id,
        "attemptId": attempt_id,
        "operation": operation,
        "status": "FAILED",
        "errorMessage": message,
    }
