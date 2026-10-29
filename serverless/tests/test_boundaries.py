"""Executable architecture rules for the currently implemented prototype."""

import ast
from importlib.util import resolve_name
from pathlib import Path

import pytest

import video_serverless

ROOT = Path(video_serverless.__file__).parent
PURE_STDLIB = {"dataclasses", "typing", "collections", "abc", "enum", "decimal", "datetime", "math", "__future__"}


def forbidden_imports(source, package, allowed):
    imports = []
    for node in ast.walk(ast.parse(source)):
        if isinstance(node, ast.Import):
            imports.extend(item.name for item in node.names)
        elif isinstance(node, ast.ImportFrom):
            name = node.module or ""
            imports.append(resolve_name("." * node.level + name, package) if node.level else name)
    return [name for name in imports if not any(name == prefix or name.startswith(prefix + ".") for prefix in allowed)]


def test_business_and_ports_only_depend_on_pure_values_and_capabilities():
    for folder in ["business", "ports"]:
        for path in (ROOT / folder).rglob("*.py"):
            parts = path.relative_to(ROOT).with_suffix("").parts
            package = "video_serverless." + ".".join(parts[:-1])
            allowed = PURE_STDLIB | {"video_serverless.ports"}
            if folder == "business":
                allowed.add("video_serverless.business")
            assert not forbidden_imports(path.read_text(encoding="utf-8"), package, allowed), path


@pytest.mark.parametrize("source,package", [
    ("import os", "video_serverless.business"),
    ("from pymongo import MongoClient", "video_serverless.business"),
    ("from fastapi import Request", "video_serverless.business"),
    ("from ..adapters.mongo import MongoPublicVideoProbe", "video_serverless.business"),
    ("from ..http import Request", "video_serverless.business"),
    ("from ..business.prototype import Prototype", "video_serverless.ports"),
])
def test_guard_rejects_framework_provider_io_and_reversed_dependencies(source, package):
    assert forbidden_imports(source, package, PURE_STDLIB | {"video_serverless.ports"})


def test_http_controller_has_no_configuration_or_infrastructure_imports():
    allowed = {"hmac", "threading", "uuid", "video_serverless.business", "video_serverless.http"}
    source = (ROOT / "application.py").read_text(encoding="utf-8")
    assert not forbidden_imports(source, "video_serverless", allowed)
    source = (ROOT / "http.py").read_text(encoding="utf-8")
    assert not forbidden_imports(source, "video_serverless", PURE_STDLIB | {"json"})
    from video_serverless import http

    assert not hasattr(http.Response, "fc")
    assert not hasattr(http, "from_fc")
    source = (ROOT / "user_responses.py").read_text(encoding="utf-8")
    assert not forbidden_imports(source, "video_serverless", PURE_STDLIB | {
        "video_serverless.http", "video_serverless.ports"
    })
    source = (ROOT / "user_application.py").read_text(encoding="utf-8")
    assert not forbidden_imports(source, "video_serverless", PURE_STDLIB | {
        "video_serverless.business", "video_serverless.http", "video_serverless.ports",
        "video_serverless.profile_json", "video_serverless.request_cycle", "video_serverless.user_responses",
    })
    for name in (
        "public_video_application.py", "backend_application.py", "search_application.py", "watch_application.py",
        "playlist_application.py", "watch_log_application.py", "heartbeat_application.py", "playback_application.py",
        "viewing_application.py", "routing_application.py",
        "upload_application.py",
        "file_upload_application.py",
        "video_status_application.py",
        "local_cover_application.py",
        "local_transcode_application.py",
        "comment_application.py",
        "comment_write_application.py",
        "video_reaction_application.py",
    ):
        source = (ROOT / name).read_text(encoding="utf-8")
        assert not forbidden_imports(source, "video_serverless", PURE_STDLIB | {
            "re", "video_serverless.business", "video_serverless.http", "video_serverless.ports",
            "video_serverless.local_cover_application",
            "video_serverless.local_transcode_application",
            "video_serverless.comment_application",
            "video_serverless.comment_json",
            "video_serverless.comment_write_application",
            "video_serverless.java_numbers",
            "video_serverless.upload_json",
            "video_serverless.profile_json",
            "video_serverless.playback_json",
            "video_serverless.request_cycle", "video_serverless.user_application",
            "video_serverless.public_video_application",
            "video_serverless.video_responses", "video_serverless.search_application",
            "video_serverless.playlist_application", "video_serverless.watch_application",
            "video_serverless.heartbeat_json",
            "video_serverless.playback_json",
            "video_serverless.viewing_application", "video_serverless.heartbeat_application",
            "video_serverless.history_progress_application", "video_serverless.playback_application",
            "video_serverless.watch_log_application",
            "video_serverless.routing_application", "urllib.parse",
            "video_serverless.upload_application", "video_serverless.upload_json",
            "video_serverless.file_upload_application",
            "video_serverless.video_status_application",
            "video_serverless.video_reaction_application",
        })
    source = (ROOT / "request_cycle.py").read_text(encoding="utf-8")
    assert not forbidden_imports(source, "video_serverless", PURE_STDLIB | {
        "video_serverless.http", "video_serverless.ports", "video_serverless.business"
    })
