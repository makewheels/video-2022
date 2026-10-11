"""自建 CPU 函数入口：FC custom runtime HTTP server，监听 0.0.0.0:9000。

POST 任意路径即任务请求。校验、执行、回调、清理全部同步完成后才返回 2xx；
执行失败也返回 200（错误经回调传达），避免 FC 平台异步重试与应用层重试叠乘。
"""
import json
import os
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

sys.path.insert(0, "/usr/src/app")

from common import job, protocol  # noqa: E402
from cpu import cover, probe, remux  # noqa: E402

INVOKE_SECRET = os.environ.get("INVOKE_SECRET", "")

HANDLERS = {
    "PROBE": probe.handle_probe,
    "COVER": cover.handle_cover,
    "TRANSCODE": remux.handle_remux,
}


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):  # noqa: N802
        try:
            length = int(self.headers.get("Content-Length", 0))
            raw = self.rfile.read(length)
            body = json.loads(raw)
            os.environ["_REQUEST_INVOKE_SECRET"] = self.headers.get("X-Invoke-Secret", "")
            protocol.validate_request(body, INVOKE_SECRET)

            handler = HANDLERS.get(body["operation"])
            if handler is None:
                raise protocol.ProtocolError(f"CPU 函数不支持 operation = {body['operation']}")

            status = job.run_task(body, handler)
            self._respond(status, {"ok": True})
        except protocol.ProtocolError as e:
            self._respond(400, {"ok": False, "error": str(e)})
        except Exception as e:  # noqa: BLE001
            print(f"[server] 请求处理异常: {e}")
            self._respond(200, {"ok": False, "error": "internal error"})

    def do_GET(self):  # noqa: N802
        self._respond(200, {"ok": True, "service": "transcode-cpu"})

    def _respond(self, status: int, payload: dict):
        data = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, fmt, *args):  # noqa: A003
        print(f"[server] {self.address_string()} {fmt % args}")


if __name__ == "__main__":
    server = ThreadingHTTPServer(("0.0.0.0", 9000), Handler)
    print("[server] CPU 函数已启动，监听 9000")
    server.serve_forever()
