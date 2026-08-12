from __future__ import annotations

from urllib.error import HTTPError

import pytest

import video_agent.client as client_module
from video_agent.client import AgentResponse, ModelClient


def _http_error(code):
    return HTTPError("https://example.invalid", code, "error", {}, None)


def test_chat_retries_429_then_succeeds(monkeypatch):
    client = ModelClient(model="m", base_url="https://example.invalid", api_key="k")
    calls = []

    def fake(_body):
        calls.append(1)
        if len(calls) < 3:
            raise _http_error(429)
        return AgentResponse(text="ok")

    monkeypatch.setattr(client, "_handle_nonstream", fake)
    monkeypatch.setattr(client_module.time, "sleep", lambda _seconds: None)
    assert client.chat([], tools=[]).text == "ok"
    assert len(calls) == 3


def test_chat_does_not_retry_auth_failure(monkeypatch):
    client = ModelClient(model="m", base_url="https://example.invalid", api_key="k")
    calls = []

    def fake(_body):
        calls.append(1)
        raise _http_error(401)

    monkeypatch.setattr(client, "_handle_nonstream", fake)
    with pytest.raises(HTTPError):
        client.chat([], tools=[])
    assert len(calls) == 1
