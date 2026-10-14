import json
import re
import subprocess
import sys

import pytest
from fastapi.testclient import TestClient

from video_serverless import bootstrap
from video_serverless.adapters.mongo import MongoPublicVideoProbe
from video_serverless.application import Application
from video_serverless.business.prototype import Prototype
from video_serverless.fc_handler import handler
from video_serverless.ports.probe import ProbeReading
from video_serverless.web import app


def fc(path, method="GET", token=""):
    return handler({
        "requestContext": {"http": {"path": path, "method": method}},
        "headers": {"X-Prototype-Token": token},
    }, None)


def test_health_is_java_text_form_and_has_no_mongo_dependency():
    response = fc("/healthCheck")
    assert response["statusCode"] == 200
    assert re.fullmatch(r"ok \d{13}", response["body"])
    # New interpreter proves absence of transitive Mongo/ASGI imports.
    subprocess.run([sys.executable, "-c", (
        "import sys; from video_serverless.fc_handler import handler; "
        "handler({'rawPath':'/healthCheck'},None); "
        "assert 'pymongo' not in sys.modules; assert 'fastapi' not in sys.modules"
    )], check=True)


@pytest.mark.parametrize("configured,provided", [("", ""), ("secret", "wrong"), ("secret", "错误")])
def test_mongo_diagnostics_fail_closed_without_valid_secret(monkeypatch, configured, provided):
    monkeypatch.setenv("PROTOTYPE_TOKEN", configured)
    monkeypatch.setattr(MongoPublicVideoProbe, "read_public_video", lambda self: pytest.fail("must not touch Mongo"))
    assert fc("/_prototype/mongo-read", token=provided)["statusCode"] == 403


def test_probe_status_and_contract_match_both_entry_points(monkeypatch):
    monkeypatch.setenv("PROTOTYPE_TOKEN", "test-secret")
    monkeypatch.setattr(MongoPublicVideoProbe, "read_public_video", lambda self: ProbeReading(True, 0.5, 1.0))
    native = fc("/_prototype/mongo-read", token="test-secret")
    with TestClient(app) as client:
        web = client.get("/_prototype/mongo-read", headers={"X-Prototype-Token": "test-secret"})
    assert native["statusCode"] == web.status_code == 200
    assert json.loads(native["body"]) == web.json()
    assert native["headers"]["X-Prototype-Instance"] == web.headers["X-Prototype-Instance"]
    assert int(web.headers["X-Prototype-Invocation"]) == int(native["headers"]["X-Prototype-Invocation"]) + 1


def test_database_error_does_not_leak_connection_or_password(monkeypatch):
    monkeypatch.setenv("PROTOTYPE_TOKEN", "test-secret")

    def broken(self):
        raise RuntimeError("mongodb://user:sensitive-secret@private-host")

    monkeypatch.setattr(MongoPublicVideoProbe, "read_public_video", broken)
    result = fc("/_prototype/mongo-read", token="test-secret")
    assert result["statusCode"] == 503
    assert "sensitive-secret" not in result["body"]
    assert "private-host" not in result["body"]


def test_prototype_does_not_migrate_or_forward_unknown_business_routes():
    assert fc("/video/create", "POST")["statusCode"] == 404
    assert fc("/watchController/getM3u8Content.m3u8")["statusCode"] == 404


def test_methods_not_silently_reinterpreted(monkeypatch):
    monkeypatch.setenv("PROTOTYPE_TOKEN", "test-secret")
    response = fc("/_prototype/empty", "POST", "test-secret")
    assert response["statusCode"] == 405
    assert response["headers"]["Allow"] == "GET"



@pytest.mark.parametrize("path,method,token,status", [
    ("/healthCheck", "GET", "", 200),
    ("/_prototype/empty", "GET", "test-secret", 200),
    ("/_prototype/mongo-read", "GET", "test-secret", 200),
    ("/_prototype/mongo-read", "GET", "wrong", 403),
    ("/_prototype/empty", "POST", "test-secret", 405),
    ("/_prototype/empty", "POST", "wrong", 403),
    ("/video/create", "POST", "test-secret", 404),
])
def test_both_shells_preserve_success_failure_and_auth_order(monkeypatch, path, method, token, status):
    class OtherProvider:
        def read_public_video(self):
            return ProbeReading(False, 0.25, 1.5)

    shared = Application(Prototype(OtherProvider(), lambda: 1_730_000_000_000), "test-secret")
    monkeypatch.setattr(bootstrap, "get_application", lambda: shared)
    native = fc(path, method, token)
    with TestClient(app) as client:
        web = client.request(method, path, headers={"X-Prototype-Token": token})
    assert native["statusCode"] == web.status_code == status
    assert native["body"] == web.text  # Includes exact health/JSON text and empty 405.
    for name in ["Content-Type", "Allow", "Cache-Control", "X-Prototype-Instance"]:
        assert native["headers"].get(name) == web.headers.get(name)


def test_health_and_empty_in_fresh_process_do_not_import_drivers_or_web_framework(monkeypatch):
    monkeypatch.setenv("PROTOTYPE_TOKEN", "probe-token")
    subprocess.run([sys.executable, "-c", (
        "import sys; from video_serverless.fc_handler import handler; "
        "result = handler({'rawPath':'/_prototype/empty',"
        "'headers':{'X-Prototype-Token':'probe-token'}},None); "
        "assert result['statusCode']==200; "
        "assert 'pymongo' not in sys.modules; assert 'fastapi' not in sys.modules; "
        "assert 'starlette' not in sys.modules"
    )], check=True)
