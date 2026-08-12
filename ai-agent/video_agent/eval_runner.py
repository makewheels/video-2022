"""Evaluation harness — runs eval suites and grades results."""

from __future__ import annotations

import json
import time
from pathlib import Path
from typing import Any

from . import trace as lf_trace
from .eval_graders import grade_case
from .eval_user_simulator import run_scripted_scenario


def run_eval_suite(assistant, cases_path: str, *, trials: int = 1) -> dict[str, Any]:
    if trials < 1:
        raise ValueError("trials 必须 >= 1")
    cases = _load_jsonl(cases_path)
    results = []
    case_passes: dict[str, list[bool]] = {str(case["id"]): [] for case in cases}
    total_time = 0.0

    run_number = 0
    total_runs = len(cases) * trials
    for case in cases:
        query, history = _case_input(case)
        for trial in range(1, trials + 1):
            run_number += 1
            print(f"  [{run_number}/{total_runs}] {case['id']}#{trial}: {query[:60]}", end=" ", flush=True)
            trace_handle = lf_trace.start_trace(
                name=f"eval:{case['id']}",
                input=case.get("input", {"query": query}),
                environment="eval",
                tags=["eval", f"case:{case['id']}", f"trial:{trial}"],
                metadata={"trial": trial, "category": case.get("category"), "risk": case.get("risk")},
            )
            _reset(assistant)
            before = _snapshot(assistant)
            start = time.time()
            error: Exception | None = None
            try:
                if case.get("input", {}).get("turns"):
                    result = run_scripted_scenario(assistant, case["input"]["turns"])
                else:
                    result = assistant.chat(query, history=history) if history else assistant.answer(query)
            except Exception as exc:
                error = exc
                result = {"answer": str(exc), "trace": []}
            elapsed = time.time() - start
            total_time += elapsed
            after = _snapshot(assistant)

            if "expectations" in case:
                graded = grade_case(case, result, state_before=before, state_after=after)
                ok = graded.passed and error is None
                reasons = ([f"API error: {error}"] if error else []) + graded.reasons
                scores = graded.scores.copy()
                if error is not None:
                    scores["task_success"] = 0.0
                    scores["eval_pass"] = 0.0
                grade_payload = graded.as_dict()
            else:
                reasons = ([f"API error: {error}"] if error else []) + _grade(case, result)
                ok = not reasons
                scores = {"eval_pass": 1.0 if ok else 0.0}
                grade_payload = {"passed": ok, "scores": scores, "reasons": reasons}

            case_passes[str(case["id"])].append(ok)
            print(f"{'✅' if ok else '❌'} ({elapsed:.1f}s)" + (f" — {', '.join(reasons)}" if reasons else ""))
            for score_name, value in scores.items():
                lf_trace.score(
                    trace=trace_handle,
                    name=score_name,
                    value=value,
                    comment=None if value == 1.0 else "; ".join(reasons)[:1000],
                    environment="eval",
                    metadata={"elapsed": elapsed, "trial": trial},
                )
            lf_trace.end_trace(
                trace_handle,
                output={"answer": result.get("answer"), "passed": ok, "scores": scores},
                error=error,
            )
            results.append(
                {
                    "id": case["id"],
                    "trial": trial,
                    "query": query,
                    "passed": ok,
                    "reasons": reasons,
                    "grade": grade_payload,
                    "result": result,
                    "elapsed": elapsed,
                }
            )

            if run_number < total_runs:
                time.sleep(2.0)

    # eval CLI 是短生命周期进程，退出前冲一次队列
    lf_trace.flush()

    passed_cases = sum(all(outcomes) for outcomes in case_passes.values())
    return {
        "total": len(cases),
        "passed": passed_cases,
        "failed": len(cases) - passed_cases,
        "trials": trials,
        "total_runs": total_runs,
        "passed_runs": sum(item["passed"] for item in results),
        "pass_at_k": {case_id: all(outcomes) for case_id, outcomes in case_passes.items()},
        "total_time": total_time,
        "results": results,
    }


def _snapshot(assistant: Any) -> Any:
    snapshot = getattr(getattr(assistant, "tools", None), "snapshot_state", None)
    return snapshot() if callable(snapshot) else None


def _reset(assistant: Any) -> None:
    reset = getattr(getattr(assistant, "tools", None), "reset_fixture_state", None)
    if callable(reset):
        reset()


def _case_input(case: dict[str, Any]) -> tuple[str, list[dict[str, Any]] | None]:
    if "input" not in case:
        return str(case["query"]), None
    item_input = case["input"]
    if item_input.get("messages"):
        messages = list(item_input["messages"])
        user_indexes = [index for index, message in enumerate(messages) if message.get("role") == "user"]
        if not user_indexes:
            raise ValueError(f"case {case['id']} 的 messages 缺少 user 消息")
        last = user_indexes[-1]
        return str(messages[last]["content"]), messages[:last]
    return str(item_input["query"]), None


def _grade(case: dict[str, Any], result: dict[str, Any]) -> list[str]:
    reasons: list[str] = []

    # Check expected intent (legacy)
    expected_intent = case.get("expected_intent")
    if expected_intent:
        actual_intent = result.get("intent", "")
        if actual_intent != expected_intent:
            reasons.append(f"intent: expected={expected_intent}, got={actual_intent}")

    # Check answer content
    answer = result.get("answer", "")
    for text in case.get("answer_contains", []):
        if text not in answer:
            reasons.append(f"answer missing '{text}'")

    # Check tool traces
    tool_names = [call.get("name") for call in result.get("trace", [])]
    for tool in case.get("tools_include", []):
        if tool not in tool_names:
            reasons.append(f"tool '{tool}' not in trace ({', '.join(tool_names[:5])})")

    # Check write safety
    if case.get("must_not_write"):
        write_tools = {
            "upload_video", "delete_video", "update_video",
            "add_comment", "delete_comment", "like_comment",
            "like_video", "dislike_video",
            "create_playlist", "delete_playlist", "update_playlist",
            "add_video_to_playlist", "remove_video_from_playlist",
            "create_share", "mark_notification_read", "mark_all_notifications_read",
            "clear_watch_history", "transfer_youtube",
        }
        write_tool_hits = [name for name in tool_names if name in write_tools]
        if write_tool_hits:
            trace = result.get("trace", [])
            if not any(
                isinstance(call.get("result"), dict) and call["result"].get("requiresConfirmation")
                for call in trace
                if call.get("name") in write_tool_hits
            ):
                reasons.append(f"write operation executed without confirmation: {write_tool_hits}")

    return reasons


def _load_jsonl(path: str) -> list[dict[str, Any]]:
    cases = []
    with Path(path).open(encoding="utf-8") as f:
        for line in f:
            stripped = line.strip()
            if stripped and not stripped.startswith("#"):
                cases.append(json.loads(stripped))
    return cases
