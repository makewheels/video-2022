"""Langfuse trace 观测层（可选依赖，未配置时整体 no-op）。

纪律（同 speakup server/services/llm_trace.py）：
- 不配 `LANGFUSE_SECRET_KEY` / `LANGFUSE_PUBLIC_KEY` / `LANGFUSE_HOST` 时整体 no-op，本地/测试零侵入
- 任何异常只记 warning 不抛——绝不让 trace 拖垮主路径
- langfuse 是可选依赖（`uv sync --extra langfuse`），SDK 后台线程批量上报

埋点（见各调用方）：
- `ModelClient.chat` / `chat_stream` → generation（model / messages / usage / 延迟）
- `VideoTools.execute` → tool span
- `server_optimized` 的 `/chat/stream` → 每次请求一个 trace，透传 session_id
- `eval_runner` 跑分 → score（environment=eval，和线上流量隔开）

trace 结构：一个请求/一个 eval case = 一个 trace（根 span），期间产生的
generation 和 tool span 通过 contextvars 自动挂到它下面；`session_id` /
`environment` / `tags` 是 trace 级属性，经 `propagate_attributes` 下发。
"""

from __future__ import annotations

import logging
import os
import time
from typing import Any

logger = logging.getLogger(__name__)

_client: Any = None
_tried_init = False


def _get_client() -> Any | None:
    """懒加载单例。未配置 env 或 SDK 初始化失败都返回 None（= 关闭）。"""
    global _client, _tried_init
    if _tried_init:
        return _client
    _tried_init = True
    if not os.environ.get("LANGFUSE_SECRET_KEY"):
        return None
    try:
        from langfuse import Langfuse

        _client = Langfuse()  # 读 LANGFUSE_SECRET_KEY / LANGFUSE_PUBLIC_KEY / LANGFUSE_HOST
        logger.info("langfuse trace 已启用 → %s", os.environ.get("LANGFUSE_HOST", "(cloud)"))
    except Exception as e:
        logger.warning("langfuse 初始化失败，trace 关闭（不影响主路径）: %s", e)
        _client = None
    return _client


class _Handle:
    """一次观测的句柄：已 enter 的 context manager 列表 + 观测对象 + 起始时间。

    未启用 langfuse 时所有 start_* 返回 None，对应的 finish/end/score 收到 None 直接 no-op。
    """

    __slots__ = ("cms", "obs", "t0", "trace_id")

    def __init__(self) -> None:
        self.cms: list[Any] = []
        self.obs: Any = None
        self.t0: float = time.monotonic()
        self.trace_id: str | None = None


def _close(handle: _Handle) -> None:
    """按相反顺序退出所有已 enter 的 context manager（幂等）。"""
    while handle.cms:
        cm = handle.cms.pop()
        try:
            cm.__exit__(None, None, None)
        except Exception:
            pass


def _latency_ms(handle: _Handle) -> int:
    return int((time.monotonic() - handle.t0) * 1000)


def _usage_details(usage: dict | None) -> dict | None:
    """OpenAI 风格 usage → langfuse usage_details。"""
    if not usage:
        return None
    prompt = int(usage.get("prompt_tokens") or 0)
    completion = int(usage.get("completion_tokens") or 0)
    total = int(usage.get("total_tokens") or (prompt + completion))
    if not (prompt or completion or total):
        return None
    return {"input": prompt, "output": completion, "total": total}


# ── generation（LLM 调用） ─────────────────────────────────────


def start_generation(
    *,
    name: str = "model.chat",
    model: str | None = None,
    messages: Any = None,
    metadata: dict | None = None,
) -> _Handle | None:
    """LLM 调用前开始一个 generation（span 起止真实，UI 里延迟才准）。未启用返回 None。"""
    client = _get_client()
    if client is None:
        return None
    handle = _Handle()
    try:
        handle.obs = client.start_observation(
            name=name,
            as_type="generation",
            model=model,
            input=messages,
            metadata=metadata,
        )
        return handle
    except Exception as e:
        logger.warning("langfuse start_generation 失败（不影响主路径）: %s", e)
        return None


def finish_generation(
    handle: _Handle | None,
    *,
    output: Any = None,
    usage: dict | None = None,
    error: BaseException | str | None = None,
    input: Any = None,
) -> None:
    """结束 generation 并回填 output/usage/延迟。handle 为 None（未启用）时什么都不做。

    input：HTTP 层完整请求体快照（含 messages/tools/全部参数），提供时覆盖
    start 时的 messages 快照——Langfuse 里看到的即线上发送原文，一字不少。
    """
    if handle is None:
        return
    try:
        if handle.obs is not None:
            update_kw: dict[str, Any] = {
                "output": output,
                "usage_details": _usage_details(usage),
                "level": "ERROR" if error else "DEFAULT",
                "status_message": str(error) if error else None,
                "metadata": {"latencyMs": _latency_ms(handle)},
            }
            if input is not None:
                update_kw["input"] = input
            handle.obs.update(**update_kw)
            handle.obs.end()
    except Exception as e:
        logger.warning("langfuse finish_generation 失败（不影响主路径）: %s", e)
    finally:
        _close(handle)


# ── tool span（工具执行） ──────────────────────────────────────


def start_tool_span(name: str, args: dict | None = None) -> _Handle | None:
    """工具执行前开始一个 tool span。未启用返回 None。"""
    client = _get_client()
    if client is None:
        return None
    handle = _Handle()
    try:
        handle.obs = client.start_observation(name=f"tool:{name}", as_type="tool", input=args)
        return handle
    except Exception as e:
        logger.warning("langfuse start_tool_span 失败（不影响主路径）: %s", e)
        return None


def finish_tool_span(
    handle: _Handle | None,
    *,
    result: Any = None,
    error: BaseException | str | None = None,
) -> None:
    """结束 tool span。result 是带 "error" 键的 dict 也视为失败。"""
    if handle is None:
        return
    failed = error is not None or (isinstance(result, dict) and "error" in result)
    try:
        if handle.obs is not None:
            handle.obs.update(
                output=result,
                level="ERROR" if failed else "DEFAULT",
                status_message=(str(error) if error else (result.get("error") if failed else None)),
                metadata={"latencyMs": _latency_ms(handle)},
            )
            handle.obs.end()
    except Exception as e:
        logger.warning("langfuse finish_tool_span 失败（不影响主路径）: %s", e)
    finally:
        _close(handle)


# ── trace（一次请求 / 一个 eval case） ─────────────────────────


def start_trace(  # noqa: PLR0913
    *,
    name: str,
    input: Any = None,
    session_id: str | None = None,
    user_id: str | None = None,
    environment: str | None = None,
    tags: list[str] | None = None,
    metadata: dict | None = None,
) -> _Handle | None:
    """开始一个 trace 并设为当前上下文——期间产生的 generation/tool span 都挂在它下面。

    session_id / user_id / environment / tags 是 trace 级属性，经 propagate_attributes 下发。
    返回的句柄带 trace_id（供 score 用）；未启用返回 None。
    """
    client = _get_client()
    if client is None:
        return None
    handle = _Handle()
    try:
        from langfuse import propagate_attributes

        kw: dict[str, Any] = {"trace_name": name}
        if session_id:
            kw["session_id"] = session_id
        if user_id:
            kw["user_id"] = user_id
        if environment:
            kw["environment"] = environment
        if tags:
            kw["tags"] = tags
        cm = propagate_attributes(**kw)
        cm.__enter__()
        handle.cms.append(cm)

        span_cm = client.start_as_current_observation(
            name=name, as_type="span", input=input, metadata=metadata
        )
        handle.obs = span_cm.__enter__()
        handle.cms.append(span_cm)
        handle.trace_id = client.get_current_trace_id()
        return handle
    except Exception as e:
        logger.warning("langfuse start_trace 失败（不影响主路径）: %s", e)
        _close(handle)
        return None


def end_trace(
    handle: _Handle | None,
    *,
    output: Any = None,
    error: BaseException | str | None = None,
) -> None:
    """结束 trace 根 span（退出上下文；span_cm 退出时自动 end）。"""
    if handle is None:
        return
    try:
        if handle.obs is not None:
            handle.obs.update(
                output=output,
                level="ERROR" if error else "DEFAULT",
                status_message=str(error) if error else None,
                metadata={"latencyMs": _latency_ms(handle)},
            )
    except Exception as e:
        logger.warning("langfuse end_trace 失败（不影响主路径）: %s", e)
    finally:
        _close(handle)


def score(  # noqa: PLR0913
    *,
    name: str,
    value: float,
    trace: _Handle | None = None,
    trace_id: str | None = None,
    comment: str | None = None,
    environment: str | None = None,
    metadata: dict | None = None,
) -> None:
    """给某个 trace 打分（eval 用）。未启用时 no-op。"""
    client = _get_client()
    if client is None:
        return
    tid = trace_id or (trace.trace_id if trace is not None else None)
    try:
        client.create_score(
            name=name,
            value=value,
            trace_id=tid,
            comment=comment,
            environment=environment,
            metadata=metadata,
        )
    except Exception as e:
        logger.warning("langfuse score 失败（不影响主路径）: %s", e)


def flush() -> None:
    """短生命周期进程（eval CLI / 一次性脚本）退出前冲一次队列。
    长驻服务（uvicorn）靠 SDK 后台定时 flush，不用调。"""
    client = _get_client()
    if client is None:
        return
    try:
        client.flush()
    except Exception as e:
        logger.warning("langfuse flush 失败: %s", e)
