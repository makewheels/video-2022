"""Deterministic scripted user for reproducible multi-turn evaluations."""

from __future__ import annotations

from typing import Any, Mapping, Sequence


def run_scripted_scenario(assistant: Any, turns: Sequence[Mapping[str, Any]]) -> dict[str, Any]:
    """Execute user turns while exposing only prior visible conversation.

    ``grant_confirmation`` models an explicit user confirmation on that turn. It
    is applied only for the turn and recorded on resulting write-tool calls so
    safety graders can distinguish confirmed execution from an unsafe write.
    """
    if not turns:
        raise ValueError("多轮场景至少需要一个 turn")
    history: list[dict[str, Any]] = []
    transcript: list[dict[str, Any]] = []
    combined_trace: list[dict[str, Any]] = []
    tools = assistant.tools
    original_confirm = bool(tools.confirm_write)
    try:
        for index, turn in enumerate(turns):
            user_text = turn.get("user")
            if not isinstance(user_text, str) or not user_text.strip():
                raise ValueError(f"turn {index} 缺少非空 user 文本")
            granted = turn.get("grant_confirmation") is True
            tools.confirm_write = granted
            response = assistant.chat(user_text, history=history)
            answer = str(response.get("answer", ""))
            turn_trace = []
            for call in response.get("trace", []):
                normalized = dict(call)
                normalized["turn"] = index
                normalized["confirmed"] = granted
                turn_trace.append(normalized)
            combined_trace.extend(turn_trace)
            transcript.append(
                {
                    "turn": index,
                    "user": user_text,
                    "assistant": answer,
                    "grant_confirmation": granted,
                    "trace": turn_trace,
                }
            )
            history.extend(
                [
                    {"role": "user", "content": user_text},
                    {"role": "assistant", "content": answer},
                ]
            )
        return {
            "answer": "\n".join(item["assistant"] for item in transcript),
            "trace": combined_trace,
            "transcript": transcript,
        }
    finally:
        tools.confirm_write = original_confirm
