import threading
from http.server import ThreadingHTTPServer
from unittest.mock import Mock

import pytest
import requests

from video_transcode_worker.server import handler

BODY = {"jobId": "job1", "transcodeId": "tc1", "inputKey": "videos/u/202610/v/raw/f/f.mp4",
        "outputDir": "videos/u/202610/v/transcode/tc1", "resolution": "720p"}


@pytest.fixture
def endpoint():
    worker = Mock()
    server = ThreadingHTTPServer(("127.0.0.1", 0), handler(worker, "test-invoke-secret"))
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield f"http://127.0.0.1:{server.server_port}", worker
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=5)


def post(url, body, secret="test-invoke-secret"):
    return requests.post(url + "/invoke", json=body, headers={"X-Invocation-Secret": secret}, timeout=5)


def test_unauthenticated_task_cannot_execute(endpoint):
    url, worker = endpoint
    assert post(url, BODY, "wrong").status_code == 403
    worker.run.assert_not_called()


def test_invalid_task_cannot_execute(endpoint):
    url, worker = endpoint
    assert post(url, BODY | {"outputDir": "videos/other"}).status_code == 400
    worker.run.assert_not_called()


def test_valid_task_completes_and_returns_job_id(endpoint):
    url, worker = endpoint
    response = post(url, BODY)
    assert response.status_code == 200
    assert response.json() == {"jobId": "job1"}
    assert worker.run.call_args.args[0].job_id == "job1"


def test_errors_do_not_leak_credentials(endpoint):
    url, worker = endpoint
    worker.run.side_effect = RuntimeError("signed-url-secret")
    response = post(url, BODY)
    assert response.status_code == 500
    assert "signed-url-secret" not in response.text
