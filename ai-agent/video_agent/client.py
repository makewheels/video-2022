"""Model client abstraction — OpenAI-compatible by default, Anthropic optional."""

from __future__ import annotations

import json
import time
from dataclasses import dataclass, field
from typing import Any, Iterator

from . import trace as lf_trace
from .config import get_config

from .schema import ALL_TOOLS  # 数据在 schema.py，此处再导出保持既有 import 兼容


@dataclass
class ToolCall:
    id: str
    name: str
    arguments: dict[str, Any]


@dataclass
class AgentResponse:
    """One step of the agent loop."""
    text: str = ""
    tool_calls: list[ToolCall] = field(default_factory=list)
    finish_reason: str = "stop"
    usage: dict[str, int] = field(default_factory=dict)

    @property
    def has_tool_calls(self) -> bool:
        return len(self.tool_calls) > 0


class ModelClient:
    """OpenAI-compatible chat completions client.

    Also supports Anthropic Messages API via the same interface.
    """

    def __init__(self, model: str | None = None, base_url: str | None = None, api_key: str | None = None) -> None:
        cfg = get_config()
        self.base_url = base_url or cfg.base_url
        self.api_key = api_key or cfg.api_key
        self.model = model or cfg.model
        self.temperature = cfg.temperature
        self.max_tokens = cfg.max_tokens
        self.timeout = cfg.timeout

    def chat(
        self,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]] | None = None,
        *,
        stream: bool = False,
    ) -> AgentResponse:
        """Send a chat request and return the response."""
        if tools is None:
            tools = ALL_TOOLS

        body = {
            "model": self.model,
            "messages": messages,
            "temperature": self.temperature,
            "max_tokens": self.max_tokens,
            "stream": stream,
        }
        if tools:
            body["tools"] = tools
            body["tool_choice"] = "auto"

        handle = lf_trace.start_generation(name="model.chat", model=self.model, messages=messages)
        try:
            for attempt in range(3):
                try:
                    result = self._handle_stream(body) if stream else self._handle_nonstream(body)
                    break
                except Exception as exc:
                    if attempt == 2 or not _is_retryable_transport_error(exc):
                        raise
                    time.sleep(_retry_delay(exc, attempt))
        except Exception as e:
            lf_trace.finish_generation(handle, error=e)
            raise
        output: Any = result.text
        if result.tool_calls:
            output = {
                "text": result.text,
                "tool_calls": [{"name": tc.name, "arguments": tc.arguments} for tc in result.tool_calls],
            }
        lf_trace.finish_generation(handle, output=output, usage=result.usage)
        return result
    def _handle_nonstream(self, body: dict[str, Any]) -> AgentResponse:
        import urllib.request as req

        data = json.dumps(body).encode("utf-8")
        request = req.Request(
            f"{self.base_url}/chat/completions",
            data=data,
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {self.api_key}",
            },
            method="POST",
        )
        with req.urlopen(request, timeout=self.timeout) as resp:
            result = json.loads(resp.read().decode("utf-8"))

        choice = result["choices"][0]
        message = choice["message"]
        # Strip thinking content (MiniMax M2.7)
        content = message.get("content") or ""
        content = _strip_thinking(content)
        usage = result.get("usage", {})

        tool_calls = []
        for tc in message.get("tool_calls", []):
            fn = tc["function"]
            try:
                args = json.loads(fn["arguments"])
            except (json.JSONDecodeError, TypeError):
                args = {}
            tool_calls.append(ToolCall(id=tc.get("id", ""), name=fn["name"], arguments=args))

        return AgentResponse(
            text=content,
            tool_calls=tool_calls,
            finish_reason=choice.get("finish_reason", "stop"),
            usage=dict(usage),
        )

    def _handle_stream(self, body: dict[str, Any]) -> AgentResponse:
        """Streaming chat that yields deltas, returns the accumulated response."""
        # Non-streaming for simplicity in the first version;
        # streaming events are handled in the assistant layer.
        return self._handle_nonstream(body)

    def chat_stream(
        self,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]] | None = None,
    ) -> Iterator[dict[str, Any]]:
        """Yield SSE events as dicts: {"type": "text_delta", "text": "..."}
        or {"type": "tool_call_delta", ...} or {"type": "done", "usage": ...}."""
        if tools is None:
            tools = ALL_TOOLS

        import urllib.request as req

        handle = lf_trace.start_generation(name="model.chat_stream", model=self.model, messages=messages)

        body = json.dumps({
            "model": self.model,
            "messages": messages,
            "temperature": self.temperature,
            "max_tokens": self.max_tokens,
            "stream": True,
            "tools": tools,
            "tool_choice": "auto",
        }).encode("utf-8")

        request = req.Request(
            f"{self.base_url}/chat/completions",
            data=body,
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {self.api_key}",
            },
            method="POST",
        )

        try:
            with req.urlopen(request, timeout=self.timeout) as resp:
                content_parts: list[str] = []
                tool_call_buf: dict[int, dict[str, Any]] = {}
                finish_reason = ""
                usage = {}
                for line_bytes in resp:
                    line = line_bytes.decode("utf-8").strip()
                    if not line.startswith("data: "):
                        continue
                    payload = line[6:]
                    if payload == "[DONE]":
                        break
                    try:
                        event = json.loads(payload)
                    except json.JSONDecodeError:
                        continue

                    choice = event.get("choices", [{}])[0]
                    delta = choice.get("delta", {})
                    finish_reason = finish_reason or choice.get("finish_reason", "")
                    event_usage = event.get("usage")
                    if event_usage:
                        usage = event_usage

                    # Skip thinking/reasoning content (MiniMax M2.7)
                    text_delta = delta.get("content", "")
                    if text_delta and not delta.get("reasoning_content"):
                        text_delta = _strip_thinking(text_delta)
                        if text_delta:
                            content_parts.append(text_delta)
                            yield {"type": "text_delta", "text": text_delta}

                    for tc in delta.get("tool_calls", []):
                        idx = tc.get("index", 0)
                        if idx not in tool_call_buf:
                            tool_call_buf[idx] = {"id": tc.get("id", ""), "name": "", "arguments": ""}
                        if "id" in tc:
                            tool_call_buf[idx]["id"] = tc["id"]
                        if "function" in tc:
                            if "name" in tc["function"] and tc["function"]["name"]:
                                tool_call_buf[idx]["name"] += tc["function"]["name"]
                            if "arguments" in tc["function"]:
                                tool_call_buf[idx]["arguments"] += tc["function"]["arguments"]
                                yield {"type": "tool_call_delta", "index": idx, "name": tool_call_buf[idx]["name"], "arguments": tool_call_buf[idx]["arguments"]}

                # Build final tool_calls
                tool_calls = []
                for idx in sorted(tool_call_buf):
                    buf = tool_call_buf[idx]
                    try:
                        args = json.loads(buf["arguments"])
                    except (json.JSONDecodeError, TypeError):
                        args = {}
                    tool_calls.append(ToolCall(id=buf["id"], name=buf["name"], arguments=args))

                text = "".join(content_parts)
                done_output: Any = text
                if tool_calls:
                    done_output = {
                        "text": text,
                        "tool_calls": [{"name": tc.name, "arguments": tc.arguments} for tc in tool_calls],
                    }
                lf_trace.finish_generation(handle, output=done_output, usage=usage)
                handle = None  # 已 finish，防止 finally 重复登记

                yield {
                    "type": "done",
                    "text": text,
                    "tool_calls": tool_calls,
                    "finish_reason": finish_reason,
                    "usage": dict(usage) if usage else {},
                }
        except Exception as e:
            lf_trace.finish_generation(handle, error=e)
            handle = None  # 已 finish，防止 finally 重复登记
            raise
        finally:
            # 消费方提前放弃生成器（GeneratorExit）或异常路径：把手上的部分结果登记掉
            if handle is not None:
                lf_trace.finish_generation(handle, error="stream closed before done")


def _is_retryable_transport_error(exc: BaseException) -> bool:
    code = getattr(exc, "code", None) or getattr(exc, "status_code", None)
    return code == 429 or (isinstance(code, int) and 500 <= code < 600)


def _retry_delay(exc: BaseException, attempt: int) -> float:
    headers = getattr(exc, "headers", None)
    retry_after = headers.get("Retry-After") if headers is not None else None
    try:
        return min(max(float(retry_after), 0.0), 10.0)
    except (TypeError, ValueError):
        return 1.0 * (2**attempt)


# ── Tool definitions (OpenAI function-calling format) ────────────



def _strip_thinking(text: str) -> str:
    """Remove  thinking blocks from MiniMax M2.7 output."""
    import re
    return re.sub(r"<think>.*?</think>\s*", "", text, flags=re.DOTALL).strip()
