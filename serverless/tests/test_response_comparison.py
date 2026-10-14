import copy
import importlib.util
import json
from pathlib import Path

import pytest

SPEC = importlib.util.spec_from_file_location(
    "response_comparison", Path(__file__).parents[1] / "contracts/compare_responses.py"
)
comparison = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(comparison)


def fixture():
    return {"status": 200, "contentType": "application/json", "body": {
        "code": 0, "message": "success", "data": {"total": 2, "list": [
            {"id": "fixture-a", "title": "literal .*", "description": None,
             "coverUrl": "https://example.invalid/a.jpg?Expires=100&Signature=sample-a&OSSAccessKeyId=fixture-key"},
            {"id": "fixture-b", "title": "中文", "description": "", "coverUrl": None},
        ]},
    }}


def test_signing_time_changes_only_at_cover_url():
    old = fixture()
    new = copy.deepcopy(old)
    new["body"]["data"]["list"][0]["coverUrl"] = (
        "https://example.invalid/a.jpg?Signature=sample-b&OSSAccessKeyId=fixture-key&Expires=200"
    )
    assert comparison.compare(old, new)["match"]
    old["body"]["data"]["list"][0]["watchUrl"] = old["body"]["data"]["list"][0]["coverUrl"]
    new["body"]["data"]["list"][0]["watchUrl"] = new["body"]["data"]["list"][0]["coverUrl"]
    assert not comparison.compare(old, new)["match"]


@pytest.mark.parametrize("url", [
    "https://example.invalid/other.jpg?Expires=100&Signature=sample-a&OSSAccessKeyId=fixture-key",
    "https://other.invalid/a.jpg?Expires=100&Signature=sample-a&OSSAccessKeyId=fixture-key",
    "https://example.invalid/a.jpg?Expires=100&OSSAccessKeyId=fixture-key",
    "https://example.invalid/a.jpg?Expires=100&Signature=&OSSAccessKeyId=fixture-key",
    "https://example.invalid/a.jpg?Expires=100&Signature=sample-a&OSSAccessKeyId=other-key",
    "https://example.invalid/a.jpg?Expires=100&Signature=sample-a&Signature=sample-b&OSSAccessKeyId=fixture-key",
    "https://example.invalid/a.jpg?Expires=100&Signature=sample-a&OSSAccessKeyId=fixture-key&extra=1",
    "https://user:password@example.invalid/a.jpg?Expires=100&Signature=sample-a&OSSAccessKeyId=fixture-key",
])
def test_cover_object_identity_and_signing_presence_are_preserved(url):
    old = fixture()
    new = copy.deepcopy(old)
    new["body"]["data"]["list"][0]["coverUrl"] = url
    assert not comparison.compare(old, new)["match"]


@pytest.mark.parametrize("change", ["status", "contentType", "code", "total", "type", "null", "missing", "order"])
def test_business_and_http_differences_are_detected(change):
    old = fixture()
    new = copy.deepcopy(old)
    rows = new["body"]["data"]["list"]
    if change == "status":
        new["status"] = 401
    elif change == "contentType":
        new["contentType"] = "text/html"
    elif change == "code":
        new["body"]["code"] = 1
    elif change == "total":
        new["body"]["data"]["total"] = 2.0
    elif change == "type":
        new["body"]["code"] = False
    elif change == "null":
        rows[0]["description"] = ""
    elif change == "missing":
        del rows[0]["description"]
    else:
        rows.reverse()
    assert not comparison.compare(old, new)["match"]


def test_output_does_not_contain_response_values_or_unknown_field_names(capsys, tmp_path):
    old = fixture()
    new = copy.deepcopy(old)
    secret = "private-user-signature-token"
    new["body"]["data"]["list"][0]["title"] = secret
    old["body"][secret] = "before"
    new["body"][secret] = "after"
    paths = [tmp_path / "old.json", tmp_path / "new.json"]
    for path, value in zip(paths, [old, new]):
        path.write_text(json.dumps(value))
    assert comparison.main([str(path) for path in paths]) == 1
    output = capsys.readouterr().out
    assert secret not in output
    assert "sample-a" not in output
    assert "fixture-a" not in output
    assert str(tmp_path) not in output
    assert json.loads(output)["differences"]


@pytest.mark.parametrize("raw", [
    '{"private-user-secret":', '{"status":200,"status":401}',
    '{"status":200,"contentType":"application/json","body":NaN}',
    '{"status":200,"contentType":"application/json","body":1e999}',
    '{"status":true,"contentType":"application/json","body":null}',
    '{"status":200,"body":null}',
])
def test_bad_input_fails_with_fixed_message(raw, capsys, tmp_path):
    path = tmp_path / "private-fixture.json"
    path.write_text(raw)
    assert comparison.main([str(path), str(path)]) == 2
    assert capsys.readouterr().out == '{"error": "Invalid or unreadable response fixture"}\n'


def test_bound_and_size_limit(tmp_path, monkeypatch):
    old = {"status": 200, "contentType": "application/json", "body": [0] * 150}
    new = copy.deepcopy(old)
    new["body"] = [1] * 150
    result = comparison.compare(old, new)
    assert len(result["differences"]) == 100
    assert result["limitReached"] and not result["match"]
    monkeypatch.setattr(comparison, "MAX_INPUT_BYTES", 1)
    path = tmp_path / "large.json"
    path.write_text("{}")
    with pytest.raises(ValueError):
        comparison.read_fixture(path)
