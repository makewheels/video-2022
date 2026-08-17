"""video_agent/trace.py 的门控与埋点映射。

两条铁律：
- 未配 LANGFUSE_* → 全链路 no-op 且不抛（本地/CI 默认就是这个状态）
- 配了 → model/usage/session_id/environment 映射不错位，eval 打分进 environment=eval

langfuse 是可选依赖，测试不装真 SDK：用 sys.modules 注入 fake langfuse 模块。
"""

import contextlib
import sys
from types import SimpleNamespace

import pytest

import video_agent.trace as trace


@pytest.fixture(autouse=True)
def _reset_trace(monkeypatch):
    """每个测试前重置单例缓存，避免用例间串状态。"""
    monkeypatch.setattr(trace, "_client", None)
    monkeypatch.setattr(trace, "_tried_init", False)


def _fake_langfuse_module(captured: dict):  # noqa: C901
    class _FakeObs:
        def __init__(self, **kw):
            self.kw = kw
            self.updates: list[dict] = []
            self.ended = False

        def update(self, **kw):
            self.updates.append(kw)

        def end(self, **kw):
            self.ended = True

    class _FakeSpanCM:
        def __init__(self, obs):
            self.obs = obs

        def __enter__(self):
            return self.obs

        def __exit__(self, *a):
            captured["span_cm_exited"] = captured.get("span_cm_exited", 0) + 1

    class _FakeClient:
        def __init__(self, *a, **kw):
            captured["client_created"] = True

        def start_observation(self, **kw):
            obs = _FakeObs(**kw)
            captured.setdefault("observations", []).append(obs)
            return obs

        def start_as_current_observation(self, **kw):
            obs = _FakeObs(**kw)
            captured.setdefault("observations", []).append(obs)
            captured["root_obs"] = obs
            return _FakeSpanCM(obs)

        def get_current_trace_id(self):
            return "trace-123"

        def create_score(self, **kw):
            captured.setdefault("scores", []).append(kw)

        def flush(self):
            captured["flushed"] = captured.get("flushed", 0) + 1

    def _fake_propagate_attributes(**kw):
        captured["propagate"] = kw
        return contextlib.nullcontext()

    return SimpleNamespace(Langfuse=_FakeClient, propagate_attributes=_fake_propagate_attributes)


def _enable(monkeypatch, captured: dict) -> None:
    """注入 fake langfuse 并配上 env，让 trace 处于"已启用"状态。"""
    monkeypatch.setitem(sys.modules, "langfuse", _fake_langfuse_module(captured))
    monkeypatch.setenv("LANGFUSE_SECRET_KEY", "sk-test")
    monkeypatch.setenv("LANGFUSE_PUBLIC_KEY", "pk-test")
    monkeypatch.setenv("LANGFUSE_HOST", "http://langfuse.local:3000")


# ── 门控 ────────────────────────────────────────────────────────


def test_disabled_without_env(monkeypatch):
    monkeypatch.delenv("LANGFUSE_SECRET_KEY", raising=False)
    assert trace._get_client() is None
    assert trace.start_generation(model="m", messages=[]) is None
    assert trace.start_tool_span("list_my_videos") is None
    assert trace.start_trace(name="chat_stream") is None
    trace.finish_generation(None, output="x")  # 不抛
    trace.finish_tool_span(None, result={})  # 不抛
    trace.end_trace(None)  # 不抛
    trace.score(name="eval_pass", value=1.0)  # 不抛
    trace.flush()  # 不抛


def test_sdk_init_failure_is_safe(monkeypatch):
    monkeypatch.setenv("LANGFUSE_SECRET_KEY", "sk-test")
    monkeypatch.setitem(sys.modules, "langfuse", SimpleNamespace())  # 缺 Langfuse 属性 → 构造必炸
    assert trace._get_client() is None
    assert trace.start_generation(model="m") is None
    assert trace.start_tool_span("t") is None
    assert trace.start_trace(name="x") is None
    trace.score(name="eval_pass", value=0.0)  # 不抛
    trace.flush()  # 不抛


def test_flush_enabled(monkeypatch):
    captured: dict = {}
    _enable(monkeypatch, captured)
    trace.flush()
    assert captured["flushed"] == 1


# ── generation 映射 ─────────────────────────────────────────────


def test_generation_mapping(monkeypatch):
    captured: dict = {}
    _enable(monkeypatch, captured)
    messages = [{"role": "user", "content": "hi"}]
    handle = trace.start_generation(name="model.chat", model="MiniMax-M2.7", messages=messages)
    assert handle is not None
    trace.finish_generation(
        handle, output="你好", usage={"prompt_tokens": 3, "completion_tokens": 2, "total_tokens": 5}
    )

    obs = captured["observations"][0]
    assert obs.kw["name"] == "model.chat"
    assert obs.kw["as_type"] == "generation"
    assert obs.kw["model"] == "MiniMax-M2.7"
    assert obs.kw["input"] == messages
    assert obs.updates[0]["output"] == "你好"
    assert obs.updates[0]["usage_details"] == {"input": 3, "output": 2, "total": 5}
    assert obs.updates[0]["level"] == "DEFAULT"
    assert "latencyMs" in obs.updates[0]["metadata"]
    assert obs.ended


def test_generation_error_mapping(monkeypatch):
    captured: dict = {}
    _enable(monkeypatch, captured)
    handle = trace.start_generation(model="m", messages=[])
    trace.finish_generation(handle, error=RuntimeError("boom"))
    obs = captured["observations"][0]
    assert obs.updates[0]["level"] == "ERROR"
    assert obs.updates[0]["status_message"] == "boom"
    assert obs.ended


# ── tool span 映射 ──────────────────────────────────────────────


def test_tool_span_mapping(monkeypatch):
    captured: dict = {}
    _enable(monkeypatch, captured)
    span = trace.start_tool_span("get_video_detail", {"video_id": "v1"})
    trace.finish_tool_span(span, result={"error": "video not found"})
    obs = captured["observations"][0]
    assert obs.kw["name"] == "tool:get_video_detail"
    assert obs.kw["as_type"] == "tool"
    assert obs.kw["input"] == {"video_id": "v1"}
    assert obs.updates[0]["level"] == "ERROR"  # error dict 视为失败
    assert obs.ended


# ── trace / score 映射 ──────────────────────────────────────────


def test_trace_session_id_propagated(monkeypatch):
    captured: dict = {}
    _enable(monkeypatch, captured)
    handle = trace.start_trace(name="chat_stream", session_id="s1", input={"query": "q"})
    assert handle is not None and handle.trace_id == "trace-123"
    assert captured["propagate"]["session_id"] == "s1"
    assert captured["propagate"]["trace_name"] == "chat_stream"
    trace.end_trace(handle, output="done")
    assert captured["span_cm_exited"] == 1  # 上下文已退出
    assert captured["root_obs"].updates[0]["output"] == "done"


def test_eval_score_goes_to_eval_env(monkeypatch):
    captured: dict = {}
    _enable(monkeypatch, captured)
    handle = trace.start_trace(name="eval:01", input={"query": "q"}, environment="eval", tags=["eval", "case:01"])
    assert captured["propagate"]["environment"] == "eval"
    assert captured["propagate"]["tags"] == ["eval", "case:01"]

    trace.score(trace=handle, name="eval_pass", value=0.0, comment="intent 不符", environment="eval")
    assert captured["scores"][0]["trace_id"] == "trace-123"
    assert captured["scores"][0]["environment"] == "eval"
    assert captured["scores"][0]["value"] == 0.0
    trace.end_trace(handle)


# ── 调用方埋点 wiring ───────────────────────────────────────────


def test_model_client_chat_wiring(monkeypatch):
    """ModelClient.chat：fake langfuse 下 generation 记录完整请求体（含 tools）与原始响应。"""
    captured: dict = {}
    _enable(monkeypatch, captured)
    from video_agent.client import AgentResponse, ModelClient

    raw_response = {"choices": [{"message": {"content": "你好"}}], "usage": {"prompt_tokens": 3}}
    client = ModelClient(model="fake-model", base_url="http://fake", api_key="k")
    monkeypatch.setattr(
        client,
        "_handle_nonstream",
        lambda body: (
            AgentResponse(text="你好", usage={"prompt_tokens": 3, "completion_tokens": 2, "total_tokens": 5}),
            raw_response,
        ),
    )
    resp = client.chat([{"role": "user", "content": "hi"}])
    assert resp.text == "你好"

    obs = captured["observations"][0]
    assert obs.kw["name"] == "model.chat"
    assert obs.kw["model"] == "fake-model"
    # input = 发给 LLM 的完整请求体：messages + tools + 参数，一字不少
    full_input = obs.updates[0]["input"]
    assert full_input["model"] == "fake-model"
    assert full_input["messages"] == [{"role": "user", "content": "hi"}]
    assert len(full_input["tools"]) > 0 and full_input["tool_choice"] == "auto"
    assert "temperature" in full_input and "max_tokens" in full_input
    # output = 原始响应 JSON（不再是摘要）
    assert obs.updates[0]["output"] == raw_response
    assert obs.updates[0]["usage_details"] == {"input": 3, "output": 2, "total": 5}
    assert obs.ended


def test_model_client_chat_noop_when_disabled(monkeypatch):
    monkeypatch.delenv("LANGFUSE_SECRET_KEY", raising=False)
    from video_agent.client import AgentResponse, ModelClient

    client = ModelClient(model="fake-model", base_url="http://fake", api_key="k")
    monkeypatch.setattr(client, "_handle_nonstream", lambda body: (AgentResponse(text="ok"), {}))
    assert client.chat([{"role": "user", "content": "hi"}]).text == "ok"


class _FakeHTTPResponse:
    def __init__(self, lines):
        self._lines = lines

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False

    def __iter__(self):
        return iter(self._lines)


_STREAM_LINES = [
    'data: {"choices": [{"delta": {"content": "你"}}]}\n'.encode("utf-8"),
    (
        'data: {"choices": [{"delta": {"content": "好"}, "finish_reason": "stop"}], '
        '"usage": {"prompt_tokens": 1, "completion_tokens": 2, "total_tokens": 3}}\n'
    ).encode("utf-8"),
    b"data: [DONE]\n",
]


def _stub_urlopen(monkeypatch):
    import urllib.request

    monkeypatch.setattr(urllib.request, "urlopen", lambda req, timeout=None: _FakeHTTPResponse(_STREAM_LINES))


def test_chat_stream_wiring(monkeypatch):
    """ModelClient.chat_stream：事件序列不变，generation 记录完整请求体 + SSE 原文 + usage。"""
    captured: dict = {}
    _enable(monkeypatch, captured)
    _stub_urlopen(monkeypatch)
    from video_agent.client import ModelClient

    client = ModelClient(model="fake-model", base_url="http://fake", api_key="k")
    events = list(client.chat_stream([{"role": "user", "content": "hi"}]))

    assert [e["type"] for e in events] == ["text_delta", "text_delta", "done"]
    assert events[-1]["text"] == "你好"
    assert events[-1]["usage"]["total_tokens"] == 3

    obs = captured["observations"][0]
    assert obs.kw["name"] == "model.chat_stream"
    # input = 完整请求体（含 tools）；output = SSE 事件原文 + 拼装结果
    full_input = obs.updates[0]["input"]
    assert full_input["messages"] == [{"role": "user", "content": "hi"}]
    assert len(full_input["tools"]) > 0 and full_input["stream"] is True
    stream_output = obs.updates[0]["output"]
    assert len(stream_output["events"]) == 2  # 两个 data: 事件（[DONE] 不计）
    assert stream_output["assembled"] == "你好"
    assert obs.updates[0]["usage_details"] == {"input": 1, "output": 2, "total": 3}
    assert obs.ended


def test_chat_stream_early_close_finishes_generation(monkeypatch):
    """消费方提前放弃生成器：finally 兜底把手上的 span 登记掉，不泄漏。"""
    captured: dict = {}
    _enable(monkeypatch, captured)
    _stub_urlopen(monkeypatch)
    from video_agent.client import ModelClient

    client = ModelClient(model="fake-model", base_url="http://fake", api_key="k")
    gen = client.chat_stream([{"role": "user", "content": "hi"}])
    next(gen)  # 拿到第一个 text_delta
    gen.close()  # 提前关闭 → GeneratorExit → finally

    obs = captured["observations"][0]
    assert obs.updates[0]["level"] == "ERROR"
    assert obs.updates[0]["status_message"] == "stream closed before done"
    assert obs.ended


def test_chat_stream_noop_when_disabled(monkeypatch):
    monkeypatch.delenv("LANGFUSE_SECRET_KEY", raising=False)
    _stub_urlopen(monkeypatch)
    from video_agent.client import ModelClient

    client = ModelClient(model="fake-model", base_url="http://fake", api_key="k")
    events = list(client.chat_stream([{"role": "user", "content": "hi"}]))
    assert [e["type"] for e in events] == ["text_delta", "text_delta", "done"]


def test_chat_stream_http_error_finishes_once(monkeypatch):
    """HTTP 层直接失败：generation 只登记一次（except 与 finally 不得重复 finish）。"""
    captured: dict = {}
    _enable(monkeypatch, captured)
    import urllib.request

    def _boom(req, timeout=None):
        raise OSError("conn refused")

    monkeypatch.setattr(urllib.request, "urlopen", _boom)
    from video_agent.client import ModelClient

    client = ModelClient(model="fake-model", base_url="http://fake", api_key="k")
    with pytest.raises(OSError):
        list(client.chat_stream([{"role": "user", "content": "hi"}]))

    obs = captured["observations"][0]
    assert len(obs.updates) == 1
    assert obs.updates[0]["level"] == "ERROR"
    assert "conn refused" in obs.updates[0]["status_message"]
    assert obs.ended


def test_tools_execute_wiring(monkeypatch):
    """VideoTools.execute：fake langfuse 下产生 tool span。"""
    captured: dict = {}
    _enable(monkeypatch, captured)
    from video_agent.tools import VideoTools

    result = VideoTools(backend="fixture").execute("get_my_info", {})
    assert result["id"] == "fixture-user"

    obs = captured["observations"][0]
    assert obs.kw["name"] == "tool:get_my_info"
    assert obs.kw["as_type"] == "tool"
    assert obs.updates[0]["level"] == "DEFAULT"
    assert obs.ended


def test_tools_execute_error_marks_span(monkeypatch):
    captured: dict = {}
    _enable(monkeypatch, captured)
    from video_agent.tools import VideoTools

    result = VideoTools(backend="fixture").execute("get_video_detail", {"video_id": "不存在的id"})
    assert "error" in result

    obs = captured["observations"][0]
    assert obs.updates[0]["level"] == "ERROR"
    assert obs.ended


def test_tools_execute_noop_when_disabled(monkeypatch):
    monkeypatch.delenv("LANGFUSE_SECRET_KEY", raising=False)
    from video_agent.tools import VideoTools

    assert VideoTools(backend="fixture").execute("get_my_info", {})["id"] == "fixture-user"
