"""任务执行框架：临时目录管理、原片下载、执行、产物与 manifest 上传、回调、清理。"""
import json
import os
import shutil
import tempfile

from common.oss_client import OssClient
from common.protocol import (SCHEMA_VERSION, build_failure_payload,
                             build_success_manifest, send_callback)

CALLBACK_SECRET_ENV = "CALLBACK_SECRET"


def run_task(body: dict, handler) -> int:
    """执行单个任务。handler(input_path, work_dir, oss, body) -> manifest 内容（dict）。

    任何异常都转为 FAILED 回调；返回值仅作为函数 HTTP 响应（始终 200，避免 FC 平台重试叠加）。
    """
    task_id = body.get("taskId", "unknown")
    attempt_id = body.get("attemptId", "unknown")
    operation = body.get("operation", "unknown")
    callback_secret = os.environ.get(CALLBACK_SECRET_ENV, "")

    work_dir = tempfile.mkdtemp(prefix=f"vc-{task_id}-")
    print(f"[job] 任务开始 taskId = {task_id}, operation = {operation}, workDir 已创建")
    try:
        input_path = os.path.join(work_dir, "input" + os.path.splitext(body.get("inputKey", ""))[1].lower())
        oss = OssClient(body["bucket"], body["endpoint"])
        oss.download(body["inputKey"], input_path)

        manifest = handler(input_path, work_dir, oss, body)

        manifest_key = f"{body['outputDir'].rstrip('/')}/result.json"
        oss.upload_text(json.dumps(manifest, ensure_ascii=False), manifest_key)
        print(f"[job] 任务完成 taskId = {task_id}, manifest 已上传")

        callback = build_success_manifest(task_id, attempt_id, operation,
                                          outputManifestKey=manifest_key)
        send_callback(body["callbackUrl"], callback, callback_secret)
        return 200
    except Exception as e:  # noqa: BLE001 顶层兜底，错误经回调传达
        print(f"[job] 任务失败 taskId = {task_id}: {e}")
        send_callback(body["callbackUrl"],
                      build_failure_payload(task_id, attempt_id, operation, str(e)),
                      callback_secret)
        return 200
    finally:
        shutil.rmtree(work_dir, ignore_errors=True)
        print(f"[job] 临时目录已清理 taskId = {task_id}")


def upload_all_files(oss: OssClient, local_dir: str, output_dir: str) -> list:
    """上传目录下全部文件到 outputDir（key = outputDir/文件名），返回 objects 清单。"""
    objects = []
    for name in sorted(os.listdir(local_dir)):
        local_path = os.path.join(local_dir, name)
        if not os.path.isfile(local_path):
            continue
        key = f"{output_dir.rstrip('/')}/{name}"
        oss.upload_file(local_path, key)
        objects.append({"key": key, "size": os.path.getsize(local_path),
                        "type": "playlist" if name.endswith(".m3u8") else "segment"})
    return objects


def read_manifest(manifest: dict) -> dict:
    """确保 manifest 符合协议（schemaVersion 等）。"""
    manifest.setdefault("schemaVersion", SCHEMA_VERSION)
    return manifest
