"""Langfuse Dataset synchronization and Experiment execution."""

from __future__ import annotations

import hashlib
import json
import os
import subprocess
import time
import uuid
from dataclasses import dataclass
from typing import Any, Mapping

from .eval_graders import grade_case
from .eval_judge import judge_case
from .eval_user_simulator import run_scripted_scenario


_ITEM_NAMESPACE = uuid.UUID("bd04d2a4-6550-4d0e-9e36-125e3a89f216")


@dataclass(frozen=True)
class DatasetSyncResult:
    dataset_name: str
    items_upserted: int
    created: bool
    schema_enforced: bool


def dataset_name_for_suite(suite: str) -> str:
    if suite not in {"smoke", "regression", "multi_turn"}:
        raise ValueError(f"不支持的 suite: {suite}")
    return f"video-2022/evals/{suite}-v1"


def stable_item_id(dataset_name: str, case_id: str) -> str:
    """Return a globally unique, stable Langfuse item UUID."""
    return str(uuid.uuid5(_ITEM_NAMESPACE, f"{dataset_name}:{case_id}"))


def _status_code(exc: BaseException) -> int | None:
    for name in ("status_code", "status"):
        value = getattr(exc, name, None)
        if isinstance(value, int):
            return value
        raw = getattr(value, "value", None)
        if isinstance(raw, int):
            return raw
    response = getattr(exc, "response", None)
    value = getattr(response, "status_code", None)
    return value if isinstance(value, int) else None


def _schema_rejected(exc: BaseException) -> bool:
    if _status_code(exc) != 400:
        return False
    body = getattr(exc, "body", None)
    rendered = json.dumps(body, ensure_ascii=False, default=str) if body is not None else str(exc)
    return "inputSchema" in rendered and "expectedOutputSchema" in rendered


def _input_schema() -> dict[str, Any]:
    return {
        "$schema": "http://json-schema.org/draft-07/schema#",
        "type": "object",
        "properties": {
            "query": {"type": "string", "minLength": 1},
            "messages": {
                "type": "array",
                "minItems": 1,
                "items": {
                    "type": "object",
                    "properties": {
                        "role": {"type": "string"},
                        "content": {"type": "string"},
                    },
                    "required": ["role", "content"],
                    "additionalProperties": True,
                },
            },
        },
        "anyOf": [{"required": ["query"]}, {"required": ["messages"]}],
        "additionalProperties": True,
    }


def _expected_output_schema() -> dict[str, Any]:
    return {
        "$schema": "http://json-schema.org/draft-07/schema#",
        "type": "object",
        "properties": {
            "answer": {"type": "object", "additionalProperties": True},
            "tools": {"type": "object", "additionalProperties": True},
            "write_safety": {"type": "object", "additionalProperties": True},
            "state": {"type": "object", "additionalProperties": True},
        },
        "anyOf": [
            {"required": ["answer"]},
            {"required": ["tools"]},
            {"required": ["write_safety"]},
            {"required": ["state"]},
        ],
        "additionalProperties": True,
    }


def sync_dataset(
    cases: list[dict[str, Any]],
    *,
    suite: str,
    client: Any | None = None,
) -> DatasetSyncResult:
    """Idempotently upsert Git cases into a Langfuse Dataset."""
    if client is None:
        from langfuse import Langfuse

        client = Langfuse()
    if not client.auth_check():
        raise RuntimeError("Langfuse 认证失败，Dataset 未同步")

    dataset_name = dataset_name_for_suite(suite)
    created = False
    schema_enforced = True
    try:
        existing = client.get_dataset(dataset_name, fetch_items_page_size=100)
        existing_metadata = getattr(existing, "metadata", None) or {}
        schema_enforced = bool(existing_metadata.get("schema_enforced", False))
    except Exception as exc:
        if _status_code(exc) != 404:
            raise
        metadata = {"repository": "video-2022", "suite": suite, "schema_version": "v1"}
        try:
            client.create_dataset(
                name=dataset_name,
                description=f"video-2022 {suite} v1；Git JSON 是版本化事实源。",
                metadata={**metadata, "schema_enforced": True},
                input_schema=_input_schema(),
                expected_output_schema=_expected_output_schema(),
            )
        except Exception as schema_error:
            if not _schema_rejected(schema_error):
                raise
            schema_enforced = False
            client.create_dataset(
                name=dataset_name,
                description=f"video-2022 {suite} v1；Git JSON 是版本化事实源。",
                metadata={
                    **metadata,
                    "schema_enforced": False,
                    "schema_validation": "Git eval_case.schema.json + video_agent.eval_dataset",
                },
            )
        created = True

    count = 0
    for case in cases:
        if suite not in case.get("suites", []):
            continue
        case_id = str(case["id"])
        client.create_dataset_item(
            dataset_name=dataset_name,
            id=stable_item_id(dataset_name, case_id),
            input=case["input"],
            expected_output=case["expectations"],
            metadata={
                "case_id": case_id,
                "suites": case["suites"],
                "category": case["category"],
                "risk": case["risk"],
                "difficulty": case["difficulty"],
                "source_refs": [source["ref"] for source in case["sources"]],
                "schema_version": "v1",
            },
        )
        count += 1
    client.flush()
    return DatasetSyncResult(
        dataset_name=dataset_name,
        items_upserted=count,
        created=created,
        schema_enforced=schema_enforced,
    )


def _state_digest(value: Any) -> str | None:
    if value is None:
        return None
    payload = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), default=str)
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def _resolve_state_path(value: Any, path: str) -> tuple[bool, Any]:
    current = value
    for segment in path.split("."):
        if isinstance(current, Mapping) and segment in current:
            current = current[segment]
        elif isinstance(current, list):
            match = next(
                (item for item in current if isinstance(item, Mapping) and str(item.get("id")) == segment),
                None,
            )
            if match is None:
                return False, None
            current = match
        else:
            return False, None
    return True, current


def _state_projection(value: Any, state_expectation: Mapping[str, Any]) -> Any:
    paths = [str(path) for path in (state_expectation.get("path_equals") or {})]
    paths.extend(str(path) for path in (state_expectation.get("path_absent") or []))
    if not paths:
        return _state_digest(value)
    return {
        "__paths__": {
            path: {"exists": exists, "value": actual}
            for path in paths
            for exists, actual in [_resolve_state_path(value, path)]
        }
    }


def _git_commit() -> str:
    try:
        return subprocess.run(
            ["git", "rev-parse", "--short=12", "HEAD"],
            text=True,
            capture_output=True,
            check=True,
            timeout=5,
        ).stdout.strip()
    except Exception:
        return "unknown"


def _git_dirty() -> bool:
    try:
        return bool(
            subprocess.run(
                ["git", "status", "--porcelain"],
                text=True,
                capture_output=True,
                check=True,
                timeout=5,
            ).stdout.strip()
        )
    except Exception:
        return True


def _apply_task_error_veto(scores: Mapping[str, float], task_error: Any) -> dict[str, float]:
    """A failed task can never be reported as a successful evaluation."""
    result = dict(scores)
    if task_error:
        result["task_success"] = 0.0
        result["eval_pass"] = 0.0
    return result


def run_dataset_experiments(  # noqa: C901, PLR0913, PLR0915
    assistant: Any,
    *,
    suite: str,
    trials: int,
    run_name: str | None = None,
    client: Any | None = None,
    inter_case_delay: float = 0.5,
    include_judge: bool = False,
) -> list[Any]:
    """Run one Langfuse Dataset Run per trial and return SDK results."""
    if trials < 1:
        raise ValueError("trials 必须 >= 1")
    if client is None:
        from langfuse import Langfuse

        client = Langfuse()
    if not client.auth_check():
        raise RuntimeError("Langfuse 认证失败，Experiment 未运行")

    from langfuse import Evaluation

    dataset_name = dataset_name_for_suite(suite)
    dataset = client.get_dataset(dataset_name, fetch_items_page_size=100)
    model = str(getattr(getattr(assistant, "client", None), "model", "unknown"))
    backend = str(getattr(getattr(assistant, "tools", None), "backend", "unknown"))
    base = run_name or f"video-2022-{suite}-{_git_commit()}"

    def task(*, item: Any, **_: Any) -> dict[str, Any]:
        reset = getattr(assistant.tools, "reset_fixture_state", None)
        if callable(reset):
            reset()
        before = assistant.tools.snapshot_state()
        item_input = item.input or {}
        try:
            if item_input.get("turns"):
                result = run_scripted_scenario(assistant, item_input["turns"])
            elif item_input.get("messages"):
                messages = list(item_input["messages"])
                last_user_index = next(
                    (index for index in range(len(messages) - 1, -1, -1) if messages[index].get("role") == "user"),
                    None,
                )
                if last_user_index is None:
                    raise ValueError("多轮输入缺少 user 消息")
                result = assistant.chat(
                    str(messages[last_user_index]["content"]),
                    history=messages[:last_user_index],
                )
            else:
                result = assistant.answer(str(item_input.get("query", "")))
            task_error = None
        except Exception as exc:
            result = {"answer": str(exc), "trace": []}
            task_error = f"{type(exc).__name__}: {exc}"
        after = assistant.tools.snapshot_state()
        state_expectation = (item.expected_output or {}).get("state", {})
        if inter_case_delay > 0:
            time.sleep(inter_case_delay)
        return {
            **result,
            "_eval_state_before": _state_projection(before, state_expectation),
            "_eval_state_after": _state_projection(after, state_expectation),
            "_eval_task_error": task_error,
        }

    def evaluator(
        *,
        input: Any,
        output: Any,
        expected_output: Any,
        metadata: Any = None,
        **_: Any,
    ) -> list[Any]:
        case = {
            "id": (metadata or {}).get("case_id", "unknown"),
            "input": input or {},
            "expectations": expected_output or {},
        }
        output_map = output if isinstance(output, Mapping) else {"answer": str(output), "trace": []}
        grade = grade_case(
            case,
            output_map,
            state_before=output_map.get("_eval_state_before"),
            state_after=output_map.get("_eval_state_after"),
        )
        task_error = output_map.get("_eval_task_error")
        scores = _apply_task_error_veto(grade.scores, task_error)
        if task_error:
            comment = f"任务异常：{task_error}"[:1000]
        else:
            comment = "通过" if grade.passed else "；".join(grade.reasons)[:1000]
        evaluations = [
            Evaluation(
                name=name,
                value=value,
                comment=comment if name == "eval_pass" else None,
                metadata={"first_error_step": grade.first_error_step, **grade.evidence},
            )
            for name, value in scores.items()
        ]
        if task_error:
            evaluations.append(Evaluation(name="task_error", value=0.0, comment=str(task_error)[:1000]))
        if include_judge and not task_error:
            try:
                judged = judge_case(case, output_map, client=assistant.client)
                evaluations.extend(
                    Evaluation(
                        name=name,
                        value=value,
                        comment=judged.reason,
                        metadata={
                            "rubric_id": judged.rubric_id,
                            "confidence": judged.confidence,
                            "evidence": judged.evidence,
                        },
                    )
                    for name, value in judged.normalized_scores().items()
                )
                evaluations.append(
                    Evaluation(
                        name="judge_confidence",
                        value=judged.confidence,
                        comment=judged.reason,
                        metadata={"rubric_id": judged.rubric_id},
                    )
                )
            except Exception as exc:
                evaluations.append(
                    Evaluation(name="judge_valid", value=0.0, comment=f"{type(exc).__name__}: {exc}"[:1000])
                )
        return evaluations

    results = []
    for trial in range(1, trials + 1):
        exact_run_name = f"{base}-trial-{trial}"
        results.append(
            dataset.run_experiment(
                name=f"video-2022 {suite} deterministic evaluation",
                run_name=exact_run_name,
                description="Git-backed Dataset；确定性 grader；安全维度使用 veto。",
                task=task,
                evaluators=[evaluator],
                max_concurrency=1,
                metadata={
                    "repository": "video-2022",
                    "commit": _git_commit(),
                    "working_tree_dirty": str(_git_dirty()).lower(),
                    "model": model,
                    "backend": backend,
                    "suite": suite,
                    "trial": str(trial),
                    "environment": os.getenv("LANGFUSE_TRACING_ENVIRONMENT", "eval"),
                    "judge": str(include_judge).lower(),
                },
            )
        )
    client.flush()
    return results
