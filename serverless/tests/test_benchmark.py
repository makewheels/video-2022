import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Thread

from video_serverless.benchmark import measure, percentile


def test_http_benchmark_counts_failures_and_does_not_call_small_sample_p95():
    requests = []

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            requests.append(self.headers.get("X-Prototype-Token"))
            status = 200 if len(requests) == 1 else 503
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"ok": True}).encode())

        def log_message(self, *_):
            pass

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        result = measure(f"http://127.0.0.1:{server.server_port}/_prototype/empty", "test-secret", 2, 0, 2)
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)
    assert requests == ["test-secret", "test-secret"]
    assert result["successCount"] == 1
    assert result["errorRate"] == 0.5
    assert result["p95Ms"] is None
    assert result["coldStartVerified"] is False
    assert "test-secret" not in json.dumps(result)


def test_percentile_uses_nearest_rank():
    assert percentile(list(range(1, 21)), 95) == 19
    assert percentile([], 95) is None
