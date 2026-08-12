"""eval_dataset 的紧凑测试：覆盖合法用例、各类校验错误、加载行为与 CLI。"""

from __future__ import annotations

import json

import pytest

from video_agent.eval_dataset import (
    EvalDatasetError,
    _cli,
    load_eval_cases,
    validate_eval_cases,
    validate_eval_file,
)


def _base(**over):
    """返回一份合法用例的深拷贝（每次调用都是全新嵌套结构）。"""
    c = {
        "id": "c1",
        "suites": ["s1"],
        "category": "cat",
        "risk": "low",
        "difficulty": "easy",
        "input": {"query": "hello"},
        "expectations": {"answer": "ans"},
        "sources": [{"type": "product_requirement", "ref": "r1", "rationale": "because"}],
    }
    c.update(over)
    return c


def _write(tmp_path, cases):
    """把若干 case 对象写成 JSONL，返回路径。"""
    p = tmp_path / "cases.jsonl"
    p.write_text("".join(json.dumps(c) + "\n" for c in cases), encoding="utf-8")
    return p


# ---- 合法用例 --------------------------------------------------------------


def test_valid_single_turn(tmp_path):
    p = _write(tmp_path, [_base()])
    assert validate_eval_file(p) == []
    assert validate_eval_cases([_base()]) == []


def test_valid_multi_turn(tmp_path):
    case = _base(
        id="c2",
        input={"messages": [{"role": "user", "content": "hi"},
                            {"role": "assistant", "content": "yo"}]},
    )
    assert validate_eval_cases([case]) == []


def test_valid_scripted_turns_without_query():
    case = _base(
        input={
            "turns": [
                {"user": "删除视频"},
                {"user": "确认删除", "grant_confirmation": True},
            ]
        }
    )
    assert validate_eval_cases([case]) == []


def test_query_and_messages_coexist():
    """query 与 messages 同时存在且均合法 → 不报错。"""
    case = _base(input={"query": "hi",
                        "messages": [{"role": "user", "content": "x"}]})
    assert validate_eval_cases([case]) == []


# ---- 单字段错误 --------------------------------------------------------------

_WHITESPACE = [
    ("input.query", {"input": {"query": "   "}}),
    ("id", {"id": "   "}),
    ("category", {"category": "   "}),
]


@pytest.mark.parametrize("field,over", _WHITESPACE, ids=[f for f, _ in _WHITESPACE])
def test_whitespace_strings(field, over):
    errs = validate_eval_cases([_base(**over)])
    assert any(e.field == field and "空白" in e.reason for e in errs)


@pytest.mark.parametrize("over,field", [
    ({"risk": "bogus"}, "risk"),
    ({"difficulty": "bogus"}, "difficulty"),
    ({"input": {"messages": [{"role": "bogus", "content": "x"}]}}, "input.messages[0].role"),
    ({"sources": [{"type": "bogus", "ref": "r", "rationale": "b"}]}, "sources[0].type"),
], ids=["risk", "difficulty", "role", "source-type"])
def test_invalid_enum(over, field):
    errs = validate_eval_cases([_base(**over)])
    assert any(e.field == field for e in errs)
    assert any("必须" in e.reason and "之一" in e.reason for e in errs if e.field == field)


@pytest.mark.parametrize("suites,needle", [
    ([], "不能为空"),
    (["s1", "s1"], "重复"),
], ids=["empty", "duplicate"])
def test_suites_empty_or_dup(suites, needle):
    errs = validate_eval_cases([_base(suites=suites)])
    assert any(e.field.startswith("suites") and needle in e.reason for e in errs)


def test_neither_query_messages_nor_turns():
    errs = validate_eval_cases([_base(input={})])
    assert any(e.field == "input" and "至少" in e.reason for e in errs)


def test_empty_messages():
    errs = validate_eval_cases([_base(input={"messages": []})])
    assert any(e.field == "input.messages" and "不能为空" in e.reason for e in errs)


@pytest.mark.parametrize(
    "turns,field",
    [
        ([], "input.turns"),
        ([{"user": "  "}], "input.turns[0].user"),
        ([{"user": "确认", "grant_confirmation": "yes"}], "input.turns[0].grant_confirmation"),
    ],
    ids=["empty", "blank-user", "non-boolean-confirmation"],
)
def test_invalid_scripted_turns(turns, field):
    errs = validate_eval_cases([_base(input={"turns": turns})])
    assert any(error.field == field for error in errs)


def test_source_missing_rationale():
    src = {"type": "product_requirement", "ref": "r"}  # 无 rationale
    errs = validate_eval_cases([_base(sources=[src])])
    assert any(e.field == "sources[0].rationale" and "rationale" in e.reason for e in errs)


def test_critical_missing_write_safety():
    case = _base(risk="critical", expectations={"answer": "ans"})
    errs = validate_eval_cases([case])
    assert any(e.field == "expectations.write_safety" for e in errs)


# ---- 聚合 / 行号 / 重复 id --------------------------------------------------


def test_duplicate_id_line_case_field(tmp_path):
    p = _write(tmp_path, [_base(id="dup"), _base(id="dup", input={"query": "other"})])
    errs = validate_eval_file(p)
    dup = [e for e in errs if e.field == "id" and "重复" in e.reason]
    assert dup, "expected duplicate-id error"
    e = dup[0]
    assert e.line == 2                      # 重复出现在第 2 行
    assert e.case_id == "dup"
    assert "line 1" in e.reason             # 指向首次出现位置


def test_multiple_errors_aggregated_and_sorted(tmp_path):
    case = _base(risk="bogus", difficulty="bogus", input={"query": "   "})
    p = _write(tmp_path, [case])
    errs = validate_eval_file(p)
    assert len(errs) >= 3
    # validate_eval_file 稳定排序：按 line → case_id → field → reason
    keys = [(e.line or 0, e.case_id or "", e.field, e.reason) for e in errs]
    assert keys == sorted(keys)


# ---- 加载层 ----------------------------------------------------------------


def test_invalid_json_and_non_object(tmp_path):
    p = tmp_path / "bad.jsonl"
    p.write_text('{"id": "x"}\nnot json\n[1, 2]\n42\n', encoding="utf-8")
    with pytest.raises(EvalDatasetError) as exc:
        load_eval_cases(p)
    errs = exc.value.errors
    assert [e.line for e in errs] == [2, 3, 4]
    assert "非法 JSON" in errs[0].reason
    assert "顶层必须是 JSON 对象" in errs[1].reason
    assert "顶层必须是 JSON 对象" in errs[2].reason
    # validate_eval_file 同样收集，不抛
    ferrs = validate_eval_file(p)
    assert any(e.line == 2 and "非法 JSON" in e.reason for e in ferrs)
    assert sum("顶层必须是 JSON 对象" in e.reason for e in ferrs) == 2


def test_load_preserves_unknown_fields(tmp_path):
    case = _base(extra_top="x", nested={"k": 1})
    case["input"]["extra_in"] = [1, 2]
    p = _write(tmp_path, [case])
    loaded = load_eval_cases(p)
    assert loaded[0]["extra_top"] == "x"
    assert loaded[0]["nested"] == {"k": 1}
    assert loaded[0]["input"]["extra_in"] == [1, 2]


# ---- CLI -------------------------------------------------------------------


def test_cli_success_counts(capsys, tmp_path):
    p = _write(tmp_path, [_base(), _base(id="c2")])
    assert _cli(["validate", str(p)]) == 0
    out = capsys.readouterr().out
    assert "2 条用例校验通过" in out


def test_cli_failure_stderr_localization(capsys, tmp_path):
    p = _write(tmp_path, [_base(risk="bogus")])
    assert _cli(["validate", str(p)]) == 1
    err = capsys.readouterr().err
    assert "处错误" in err
    assert "risk" in err          # stderr 含字段路径定位


def test_cli_missing_path_is_controlled(capsys, tmp_path):
    missing = tmp_path / "missing.jsonl"
    assert _cli(["validate", str(missing)]) == 1
    captured = capsys.readouterr()
    assert "无法读取文件" in captured.err
    assert "Traceback" not in captured.err
