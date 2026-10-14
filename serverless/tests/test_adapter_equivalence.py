import base64
import json

import pytest
from fastapi.testclient import TestClient

from video_serverless import bootstrap
from video_serverless.fc_handler import handler
from video_serverless.http import Response
from video_serverless.web import app


@pytest.mark.parametrize("response", [
    Response(302, b"", {"Location": "https://oss.example/segment?sign=existing"}),
    Response(200, b"#EXTM3U\nsegment.m4s?sign=existing\n", {"Content-Type": "application/vnd.apple.mpegurl"}),
    Response(200, b"\x00\xffbinary", {"Content-Type": "application/octet-stream"}),
])
def test_runtime_shells_pass_identical_body_query_path_and_responses(monkeypatch, response):
    requests = []

    class RecordingController:
        def dispatch(self, request):
            requests.append(request)
            return response

    monkeypatch.setattr(bootstrap, "get_application", lambda: RecordingController())
    body = '{"说明":"退出"}'.encode()
    native = handler({
        "requestContext": {"http": {"path": "/playback/exit", "method": "POST"}},
        "headers": {"X-Probe": "same", "Content-Type": "text/plain"},
        "queryParameters": {"watchId": "existing/id"},
        "body": base64.b64encode(body).decode(), "isBase64Encoded": True,
    }, None)
    with TestClient(app) as client:
        web = client.post(
            "/playback/exit?watchId=existing%2Fid", content=body,
            headers={"X-Probe": "same", "Content-Type": "text/plain"}, follow_redirects=False,
        )
    first, second = requests
    assert first.method == second.method == "POST"
    assert first.path == second.path == "/playback/exit"
    assert first.query == second.query == {"watchId": "existing/id"}
    assert first.body == second.body == body
    for name in ["x-probe", "content-type"]:
        assert first.header(name) == second.header(name)
    assert native["statusCode"] == web.status_code == response.status
    content = base64.b64decode(native["body"]) if native["isBase64Encoded"] else native["body"].encode()
    assert content == web.content == response.body
    for name, value in response.headers.items():
        assert native["headers"][name] == web.headers[name] == value


def test_both_shells_sanitize_provider_failure_without_diagnostic_headers(monkeypatch):
    from video_serverless.application import Application
    from video_serverless.business.prototype import Prototype

    class BrokenProvider:
        def read_public_video(self):
            raise RuntimeError("driver-specific-sensitive-connection-info")

    shared = Application(Prototype(BrokenProvider(), lambda: 0), "test-token")
    monkeypatch.setattr(bootstrap, "get_application", lambda: shared)
    native = handler({
        "rawPath": "/_prototype/mongo-read", "headers": {"X-Prototype-Token": "test-token"},
    }, None)
    with TestClient(app) as client:
        web = client.get("/_prototype/mongo-read", headers={"X-Prototype-Token": "test-token"})
    assert native["statusCode"] == web.status_code == 503
    assert json.loads(native["body"]) == web.json() == {"message": "Mongo probe unavailable"}
    assert "sensitive" not in web.text
    assert "X-Prototype-Instance" not in native["headers"]
    assert "X-Prototype-Instance" not in web.headers
