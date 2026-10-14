import importlib
import json
import re
import subprocess
import sys

import pytest
from fastapi.testclient import TestClient

from video_serverless import mongo
from video_serverless.fc_handler import handler
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
    monkeypatch.setattr(mongo, "read_probe", lambda: pytest.fail("must not touch Mongo"))
    assert fc("/_prototype/mongo-read", token=provided)["statusCode"] == 403


def test_probe_status_and_contract_match_both_entry_points(monkeypatch):
    monkeypatch.setenv("PROTOTYPE_TOKEN", "test-secret")
    monkeypatch.setattr(mongo, "read_probe", lambda: {"foundPublicVideo": True, "readMs": 1.0})
    native = fc("/_prototype/mongo-read", token="test-secret")
    with TestClient(app) as client:
        web = client.get("/_prototype/mongo-read", headers={"X-Prototype-Token": "test-secret"})
    assert native["statusCode"] == web.status_code == 200
    assert json.loads(native["body"]) == web.json()
    assert native["headers"]["X-Prototype-Instance"] == web.headers["X-Prototype-Instance"]
    assert int(web.headers["X-Prototype-Invocation"]) == int(native["headers"]["X-Prototype-Invocation"]) + 1


def test_database_error_does_not_leak_connection_or_password(monkeypatch):
    monkeypatch.setenv("PROTOTYPE_TOKEN", "test-secret")

    def broken():
        raise RuntimeError("mongodb://user:sensitive-secret@private-host")

    monkeypatch.setattr(mongo, "read_probe", broken)
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


def test_probe_has_one_bounded_read_and_does_not_return_record(monkeypatch):
    calls = []

    class Collection:
        def find_one(self, *args, **kwargs):
            calls.append((args, kwargs))
            return {"_id": "private-record"}

    class Database:
        video = Collection()

    monkeypatch.setattr(mongo, "database", lambda: Database())
    result = mongo.read_probe()
    assert calls == [(({"visibility": "PUBLIC"}, {"_id": 1}), {"max_time_ms": 1000})]
    assert result["foundPublicVideo"] is True
    assert "private-record" not in json.dumps(result)


def test_client_created_once_and_existing_pool_reused(monkeypatch):
    module = importlib.reload(mongo)
    monkeypatch.setenv("MONGODB_URI", "mongodb://localhost:27017")
    monkeypatch.setenv("MONGODB_DATABASE", "isolated-test")
    calls = []
    db = object()

    class Client:
        def __init__(self, *args, **kwargs):
            calls.append((args, kwargs))

        def __getitem__(self, name):
            assert name == "isolated-test"
            return db

    monkeypatch.setattr("pymongo.MongoClient", Client)
    try:
        assert module.database() is module.database() is db
        assert len(calls) == 1
        assert calls[0][1]["maxPoolSize"] == 5
        assert calls[0][1]["minPoolSize"] == 0
    finally:
        importlib.reload(module)
