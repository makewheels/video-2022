"""eval 数据集加载与校验（纯标准库实现）。

校验规则对齐 ``ai-agent/evals/schema/eval_case.schema.json``，并在 schema 基础上
额外要求：所有声明为非空的字符串字段，去空白后必须仍非空。不依赖 jsonschema，
只使用 Python 标准库。默认资产是便于人工审阅的 JSON 数组，同时兼容历史 JSONL。
加载返回的用例字典保持原样，不丢字段、不篡改值。
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


class EvalDatasetError(Exception):
    """加载 eval 数据集时遇到的不可恢复错误（如 JSON 全部非法、路径读不动）。"""

    def __init__(self, message: str, errors: list["EvalError"] | None = None) -> None:
        super().__init__(message)
        self.errors: list[EvalError] = errors or []


@dataclass(frozen=True)
class EvalError:
    """单条校验错误，字段稳定，便于程序消费与排序。"""

    reason: str
    line: int | None = None
    case_id: str | None = None
    field: str = ""

    def __str__(self) -> str:
        loc: list[str] = []
        if self.line is not None:
            loc.append(f"line {self.line}")
        if self.case_id:
            loc.append(f"case {self.case_id}")
        if self.field:
            loc.append(self.field)
        prefix = " ".join(loc) if loc else "<file>"
        return f"{prefix}: {self.reason}"


# ---- schema 常量 ------------------------------------------------------------

_RISK_VALUES = {"low", "medium", "high", "critical"}
_DIFFICULTY_VALUES = {"easy", "medium", "hard"}
_ROLE_VALUES = {"system", "user", "assistant", "tool"}
_SOURCE_TYPE_VALUES = {
    "product_requirement",
    "implementation_contract",
    "expert_hypothesis",
    "external_evidence",
    "production_failure",
    "synthetic_control",
}
_EXPECTATION_KEYS = {"answer", "tools", "write_safety", "state"}
_REQUIRED_FIELDS = (
    "id",
    "suites",
    "category",
    "risk",
    "difficulty",
    "input",
    "expectations",
    "sources",
)


@dataclass(frozen=True)
class _EvalRecord:
    value: dict[str, Any]
    line: int | None = None
    index: int | None = None

    @property
    def location(self) -> str:
        if self.line is not None:
            return f"line {self.line}"
        return f"数组索引 {self.index}"


def _is_nonempty_str(value: Any) -> bool:
    """判断是否为去空白后仍非空的字符串。"""
    return isinstance(value, str) and value.strip() != ""


def _enum_field(  # noqa: PLR0913
    value: Any,
    allowed: set[str],
    name: str,
    errs: list[EvalError],
    line: int | None,
    case_id: str | None,
) -> None:
    """枚举字段校验：必须是字符串且取值在允许集合内。"""
    if not isinstance(value, str):
        errs.append(EvalError(f"{name} 必须是字符串", line=line, case_id=case_id, field=name))
    elif value not in allowed:
        errs.append(
            EvalError(
                f"{name} 必须是 {sorted(allowed)} 之一，实为 {value!r}",
                line=line,
                case_id=case_id,
                field=name,
            )
        )


def _validate_case(case: Any, line: int | None = None) -> list[EvalError]:  # noqa: C901, PLR0912, PLR0915
    """校验单条用例，返回该用例的全部错误（不检查跨用例唯一性）。"""
    errs: list[EvalError] = []
    if not isinstance(case, dict):
        errs.append(EvalError("用例必须是 JSON 对象", line=line))
        return errs

    raw_id = case.get("id")
    case_id = raw_id if isinstance(raw_id, str) and raw_id.strip() else None

    def add(field: str, reason: str) -> None:
        errs.append(EvalError(reason, line=line, case_id=case_id, field=field))

    # 必填字段缺失
    for key in _REQUIRED_FIELDS:
        if key not in case:
            add(key, f"缺少必填字段 {key!r}")

    # id
    if "id" in case:
        idv = case["id"]
        if not isinstance(idv, str):
            add("id", "id 必须是字符串")
        elif idv.strip() == "":
            add("id", "id 不能为空白字符串")

    # suites：非空数组、元素唯一且为非空字符串
    if "suites" in case:
        sv = case["suites"]
        if not isinstance(sv, list):
            add("suites", "suites 必须是数组")
        else:
            if len(sv) == 0:
                add("suites", "suites 不能为空")
            seen: set[str] = set()
            for i, s in enumerate(sv):
                if not _is_nonempty_str(s):
                    add(f"suites[{i}]", "suite 必须是非空字符串")
                elif s in seen:
                    add(f"suites[{i}]", f"suites 存在重复项 {s!r}")
                else:
                    seen.add(s)

    # category
    if "category" in case:
        cv = case["category"]
        if not isinstance(cv, str):
            add("category", "category 必须是字符串")
        elif cv.strip() == "":
            add("category", "category 不能为空白字符串")

    # risk / difficulty 枚举
    if "risk" in case:
        _enum_field(case["risk"], _RISK_VALUES, "risk", errs, line, case_id)
    if "difficulty" in case:
        _enum_field(case["difficulty"], _DIFFICULTY_VALUES, "difficulty", errs, line, case_id)

    # input
    if "input" in case:
        iv = case["input"]
        if not isinstance(iv, dict):
            add("input", "input 必须是对象")
        else:
            query_ok = False
            if "query" in iv:
                qv = iv["query"]
                if not isinstance(qv, str):
                    add("input.query", "query 必须是字符串")
                elif qv.strip() == "":
                    add("input.query", "query 不能为空白字符串")
                else:
                    query_ok = True

            msgs_ok = False
            if "messages" in iv:
                mv = iv["messages"]
                if not isinstance(mv, list):
                    add("input.messages", "messages 必须是数组")
                elif len(mv) == 0:
                    add("input.messages", "messages 不能为空数组")
                else:
                    msgs_ok = True
                    for j, msg in enumerate(mv):
                        if not isinstance(msg, dict):
                            add(f"input.messages[{j}]", "消息必须是对象")
                            continue
                        for mk in ("role", "content"):
                            if mk not in msg:
                                add(f"input.messages[{j}].{mk}", f"消息缺少必填字段 {mk!r}")
                        if "role" in msg:
                            _enum_field(
                                msg["role"],
                                _ROLE_VALUES,
                                f"input.messages[{j}].role",
                                errs,
                                line,
                                case_id,
                            )
                        if "content" in msg:
                            cv2 = msg["content"]
                            if not isinstance(cv2, str):
                                add(f"input.messages[{j}].content", "content 必须是字符串")
                            elif cv2.strip() == "":
                                add(f"input.messages[{j}].content", "content 不能为空白字符串")

            turns_ok = False
            if "turns" in iv:
                tv = iv["turns"]
                if not isinstance(tv, list):
                    add("input.turns", "turns 必须是数组")
                elif len(tv) == 0:
                    add("input.turns", "turns 不能为空数组")
                else:
                    turns_ok = True
                    for j, turn in enumerate(tv):
                        if not isinstance(turn, dict):
                            add(f"input.turns[{j}]", "turn 必须是对象")
                            turns_ok = False
                            continue
                        if "user" not in turn:
                            add(f"input.turns[{j}].user", "turn 缺少必填字段 'user'")
                            turns_ok = False
                        elif not _is_nonempty_str(turn["user"]):
                            add(f"input.turns[{j}].user", "user 必须是非空字符串")
                            turns_ok = False
                        if "grant_confirmation" in turn and not isinstance(turn["grant_confirmation"], bool):
                            add(
                                f"input.turns[{j}].grant_confirmation",
                                "grant_confirmation 必须是布尔值",
                            )
                            turns_ok = False

            if not query_ok and not msgs_ok and not turns_ok:
                add("input", "input 必须至少有有效 query、有效非空 messages 或有效非空 turns")

    # expectations：至少一个已知键；risk=critical 时必须显式含 write_safety
    if "expectations" in case:
        ev = case["expectations"]
        if not isinstance(ev, dict):
            add("expectations", "expectations 必须是对象")
        else:
            if not any(k in ev for k in _EXPECTATION_KEYS):
                add(
                    "expectations",
                    f"expectations 至少需要 {sorted(_EXPECTATION_KEYS)} 之一",
                )
            if case.get("risk") == "critical" and "write_safety" not in ev:
                add(
                    "expectations.write_safety",
                    "risk=critical 时 expectations 必须显式含 write_safety",
                )

    # sources：非空数组，每项 type/ref/rationale 齐全且枚举/非空
    if "sources" in case:
        sv2 = case["sources"]
        if not isinstance(sv2, list):
            add("sources", "sources 必须是数组")
        elif len(sv2) == 0:
            add("sources", "sources 不能为空")
        else:
            for k, src in enumerate(sv2):
                if not isinstance(src, dict):
                    add(f"sources[{k}]", "来源必须是对象")
                    continue
                for sk in ("type", "ref", "rationale"):
                    if sk not in src:
                        add(f"sources[{k}].{sk}", f"来源缺少必填字段 {sk!r}")
                if "type" in src:
                    _enum_field(
                        src["type"],
                        _SOURCE_TYPE_VALUES,
                        f"sources[{k}].type",
                        errs,
                        line,
                        case_id,
                    )
                if "ref" in src:
                    if not _is_nonempty_str(src["ref"]):
                        add(f"sources[{k}].ref", "ref 必须是非空字符串")
                if "rationale" in src:
                    if not _is_nonempty_str(src["rationale"]):
                        add(f"sources[{k}].rationale", "rationale 必须是非空字符串")
                if "external_url" in src:
                    uv = src["external_url"]
                    if not isinstance(uv, str) or uv.strip() == "":
                        add(f"sources[{k}].external_url", "external_url 必须是非空字符串")
                    elif not urlparse(uv).scheme:
                        add(f"sources[{k}].external_url", "external_url 必须是合法 URI")

    return errs


def validate_eval_cases(cases: list[dict[str, Any]]) -> list[EvalError]:
    """校验一批用例数据，返回聚合后的错误列表（空列表表示全部通过）。

    不读取文件，因此错误不含行号；跨用例 id 唯一性在列表范围内检查。
    """
    errs: list[EvalError] = []
    seen: dict[str, int] = {}
    for idx, case in enumerate(cases):
        errs.extend(_validate_case(case))
        if isinstance(case, dict):
            cid = case.get("id")
            if isinstance(cid, str) and cid.strip():
                if cid in seen:
                    errs.append(
                        EvalError(
                            f"id 重复：{cid!r}（首次见于索引 {seen[cid]}）",
                            case_id=cid,
                            field="id",
                        )
                    )
                else:
                    seen[cid] = idx
    return errs


def _read_eval_records(path: str | Path) -> tuple[list[_EvalRecord], list[EvalError]]:  # noqa: C901
    """Read a pretty JSON array or legacy JSONL with source locations."""
    source = Path(path)
    records: list[_EvalRecord] = []
    errors: list[EvalError] = []
    try:
        if source.suffix.lower() == ".json":
            with source.open(encoding="utf-8") as handle:
                try:
                    payload = json.load(handle)
                except json.JSONDecodeError as exc:
                    return [], [EvalError(f"非法 JSON：{exc.msg}", line=exc.lineno)]
            if not isinstance(payload, list):
                return [], [EvalError("JSON 文件顶层必须是数组")]
            for index, value in enumerate(payload):
                if not isinstance(value, dict):
                    errors.append(
                        EvalError(
                            "数组元素必须是 JSON 对象",
                            field=f"[{index}]",
                        )
                    )
                    continue
                records.append(_EvalRecord(value=value, index=index))
            return records, errors

        with source.open(encoding="utf-8") as handle:
            for line_no, raw in enumerate(handle, start=1):
                stripped = raw.strip()
                if not stripped or stripped.startswith("#"):
                    continue
                try:
                    value = json.loads(stripped)
                except json.JSONDecodeError as exc:
                    errors.append(EvalError(f"非法 JSON：{exc.msg}", line=line_no))
                    continue
                if not isinstance(value, dict):
                    errors.append(EvalError("顶层必须是 JSON 对象", line=line_no))
                    continue
                records.append(_EvalRecord(value=value, line=line_no))
    except OSError as exc:
        return [], [EvalError(f"无法读取文件：{exc}")]
    return records, errors


def _with_record_location(error: EvalError, record: _EvalRecord) -> EvalError:
    if record.index is None:
        return error
    field = f"[{record.index}]"
    if error.field:
        field = f"{field}.{error.field}"
    return EvalError(
        reason=error.reason,
        line=error.line,
        case_id=error.case_id,
        field=field,
    )


def load_eval_cases(path: str | Path) -> list[dict[str, Any]]:
    """加载 JSON 数组或历史 JSONL（保持原字段，不篡改值）。

    JSONL 跳过空行；遇到非法 JSON、JSON 顶层非数组或元素非对象时收集错误后抛
    :class:`EvalDatasetError`。本函数只做加载，不做 schema 校验——
    schema 校验请用 :func:`validate_eval_cases` / :func:`validate_eval_file`。
    """
    records, errors = _read_eval_records(path)
    if errors:
        raise EvalDatasetError(
            f"加载 {path} 失败，共 {len(errors)} 处加载错误",
            errors=errors,
        )
    return [record.value for record in records]


def validate_eval_file(path: str | Path) -> list[EvalError]:
    """加载并校验 JSON/JSONL，聚合错误并保留行号或数组索引。"""
    records, errs = _read_eval_records(path)
    seen_locations: dict[str, str] = {}

    for record in records:
        obj = record.value
        cid = obj.get("id")
        if isinstance(cid, str) and cid.strip():
            if cid in seen_locations:
                field = "id" if record.index is None else f"[{record.index}].id"
                errs.append(
                    EvalError(
                        f"id 重复：{cid!r}（首次位于 {seen_locations[cid]}）",
                        line=record.line,
                        case_id=cid,
                        field=field,
                    )
                )
            else:
                seen_locations[cid] = record.location
        errs.extend(
            _with_record_location(error, record)
            for error in _validate_case(obj, line=record.line)
        )

    # 稳定排序：按行号、case id、字段、原因
    errs.sort(
        key=lambda e: (
            e.line if e.line is not None else 0,
            e.case_id or "",
            e.field,
            e.reason,
        )
    )
    return errs


# ---- CLI --------------------------------------------------------------------


def _cli(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="video_agent.eval_dataset",
        description="eval 数据集加载与校验工具",
    )
    sub = parser.add_subparsers(dest="command", required=True)
    v = sub.add_parser("validate", help="校验 JSON/JSONL 评测文件")
    v.add_argument("path", help="JSON 数组或 JSONL 文件路径")

    args = parser.parse_args(argv)

    if args.command == "validate":
        errs = validate_eval_file(args.path)
        if errs:
            print(f"❌ {args.path}：{len(errs)} 处错误", file=sys.stderr)
            for e in errs:
                print(str(e), file=sys.stderr)
            return 1
        cases = load_eval_cases(args.path)
        print(f"✅ {args.path}：{len(cases)} 条用例校验通过")
        return 0

    # argparse 已通过 required=True 拦截缺失/未知子命令，此处兜底
    parser.error("未知子命令")
    return 2


if __name__ == "__main__":
    sys.exit(_cli())
