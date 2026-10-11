"""Authenticated single-process prototype server for an FC custom container."""

import hmac
import json
import logging
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import oss2
import requests

from .worker import Task, Worker

logger = logging.getLogger(__name__)

def build_worker():
    token = os.environ.get("OSS_SESSION_TOKEN")
    key, secret = os.environ["OSS_ACCESS_KEY_ID"], os.environ["OSS_ACCESS_KEY_SECRET"]
    auth = oss2.StsAuth(key, secret, token) if token else oss2.Auth(key, secret)
    bucket = oss2.Bucket(auth, os.environ["OSS_ENDPOINT"], os.environ["OSS_BUCKET"], connect_timeout=30)
    callback_url, callback_secret = os.environ["CALLBACK_URL"], os.environ["CALLBACK_SECRET"]
    if not callback_url.startswith("https://"):
        raise ValueError("Callback must use HTTPS")

    def notify(job_id):
        # Do not automatically rerun the encode when delivery fails. Reinvoke the same task.
        response = requests.post(callback_url, json={"jobId": job_id},
                                 headers={"X-Callback-Secret": callback_secret},
                                 timeout=(10, 30), allow_redirects=False)
        if not 200 <= response.status_code < 300:
            raise RuntimeError("Callback rejected")

    return Worker(bucket, notify, timeout=int(os.environ.get("TRANSCODE_TIMEOUT", "3600")))


def handler(worker, invocation_secret):
    class Handler(BaseHTTPRequestHandler):
        def respond(self, status, body):
            data = json.dumps(body).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self):
            self.respond(200 if self.path == "/health" else 404, {"ok": self.path == "/health"})

        def do_POST(self):
            if self.path not in {"/", "/invoke"}:
                return self.respond(404, {"error": "Unknown endpoint"})
            supplied = self.headers.get("X-Invocation-Secret", "")
            if not hmac.compare_digest(supplied.encode(), invocation_secret.encode()):
                return self.respond(403, {"error": "Forbidden"})
            try:
                size = int(self.headers.get("Content-Length", "0"))
                if not 0 < size <= 65536:
                    return self.respond(400, {"error": "Invalid request size"})
                task = Task.parse(json.loads(self.rfile.read(size)))
            except (ValueError, TypeError, KeyError):
                return self.respond(400, {"error": "Invalid task"})
            try:
                worker.run(task)
            except Exception:  # noqa: BLE001 -- sanitize all boundary errors to avoid signed URL leakage
                # Never include SDK/HTTP errors containing signed URLs or credentials in logs/responses.
                logger.error("Transcode task failed: jobId=%s", task.job_id)
                return self.respond(500, {"error": "Task failed", "jobId": task.job_id})
            self.respond(200, {"jobId": task.job_id})

        def log_message(self, format, *args):
            pass  # Avoid request paths containing arbitrary data in access logs.

    return Handler


def serve():
    secret = os.environ["INVOCATION_SECRET"]
    if not secret or not os.environ["CALLBACK_SECRET"]:
        raise ValueError("Invocation and callback secrets are required")
    server = ThreadingHTTPServer(("0.0.0.0", int(os.environ.get("PORT", "9000"))),
                                 handler(build_worker(), secret))
    try:
        server.serve_forever()
    finally:
        server.server_close()
