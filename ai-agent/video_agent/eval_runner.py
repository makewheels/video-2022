"""Evaluation harness for the video agent prototype."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from .assistant import VideoAssistant


def run_eval_suite(assistant: VideoAssistant, cases_path: str) -> dict[str, Any]:
    cases = _load_jsonl(cases_path)
    results = []
    passed = 0
    for case in cases:
        result = assistant.answer(case["query"])
        reasons = _grade(case, result)
        ok = not reasons
        passed += 1 if ok else 0
        results.append({
            "id": case["id"],
            "query": case["query"],
            "passed": ok,
            "reasons": reasons,
            "result": result,
        })
    return {"total": len(cases), "passed": passed, "failed": len(cases) - passed, "results": results}


def _grade(case: dict[str, Any], result: dict[str, Any]) -> list[str]:
    reasons: list[str] = []
    expected_intent = case.get("expected_intent")
    if expected_intent and result.get("intent") != expected_intent:
        reasons.append(f"intent expected {expected_intent}, got {result.get('intent')}")

    answer = result.get("answer", "")
    for text in case.get("answer_contains", []):
        if text not in answer:
            reasons.append(f"answer missing {text!r}")

    tool_names = [call.get("name") for call in result.get("trace", [])]
    for tool in case.get("tools_include", []):
        if tool not in tool_names:
            reasons.append(f"tool trace missing {tool}")

    if case.get("must_not_write"):
        write_tools = {"upload_video", "delete_video", "update_video"}
        if any(name in write_tools for name in tool_names):
            trace = result.get("trace", [])
            if not any(call.get("result", {}).get("requiresConfirmation") for call in trace if isinstance(call.get("result"), dict)):
                reasons.append("write operation executed without confirmation guard")

    return reasons


def _load_jsonl(path: str) -> list[dict[str, Any]]:
    cases = []
    with Path(path).open(encoding="utf-8") as f:
        for line in f:
            stripped = line.strip()
            if stripped:
                cases.append(json.loads(stripped))
    return cases

