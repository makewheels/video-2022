from __future__ import annotations

from types import SimpleNamespace

import pytest

from video_agent.eval_langfuse import (
    _apply_task_error_veto,
    dataset_name_for_suite,
    stable_item_id,
    sync_dataset,
)


class _HttpError(Exception):
    def __init__(self, status_code, body=None):
        super().__init__(str(body))
        self.status_code = status_code
        self.body = body


class _FakeClient:
    def __init__(self, *, exists=False, reject_schema=False):
        self.exists = exists
        self.reject_schema = reject_schema
        self.created = []
        self.items = []
        self.flushed = False

    def auth_check(self):
        return True

    def get_dataset(self, name, **kwargs):
        if not self.exists:
            raise _HttpError(404, {"message": "Dataset not found"})
        return SimpleNamespace(metadata={"schema_enforced": False}, items=[])

    def create_dataset(self, **kwargs):
        self.created.append(kwargs)
        if kwargs.get("input_schema") and self.reject_schema:
            raise _HttpError(
                400,
                {"error": [{"path": ["inputSchema"]}, {"path": ["expectedOutputSchema"]}]},
            )
        self.exists = True
        return SimpleNamespace()

    def create_dataset_item(self, **kwargs):
        self.items.append(kwargs)

    def flush(self):
        self.flushed = True


def _case(case_id="c1", suites=None):
    return {
        "id": case_id,
        "suites": suites or ["smoke", "regression"],
        "category": "query",
        "risk": "low",
        "difficulty": "easy",
        "input": {"query": "几个视频"},
        "expectations": {"answer": {"contains": ["视频"]}},
        "sources": [{"type": "expert_hypothesis", "ref": "seed", "rationale": "seed"}],
    }


def test_dataset_name_and_ids_are_stable_and_global():
    assert dataset_name_for_suite("smoke") == "video-2022/evals/smoke-v1"
    assert stable_item_id("a", "c1") == stable_item_id("a", "c1")
    assert stable_item_id("a", "c1") != stable_item_id("b", "c1")
    with pytest.raises(ValueError):
        dataset_name_for_suite("unknown")


def test_sync_creates_schema_dataset_and_filters_suite():
    client = _FakeClient()
    result = sync_dataset([_case(), _case("c2", ["regression"])], suite="smoke", client=client)
    assert result.created
    assert result.schema_enforced
    assert result.items_upserted == 1
    assert client.created[0]["input_schema"]["$schema"].endswith("draft-07/schema#")
    assert client.items[0]["metadata"]["case_id"] == "c1"
    assert client.items[0]["id"] == stable_item_id(result.dataset_name, "c1")
    assert client.flushed


def test_sync_only_downgrades_known_server_schema_rejection():
    client = _FakeClient(reject_schema=True)
    result = sync_dataset([_case()], suite="smoke", client=client)
    assert result.created
    assert not result.schema_enforced
    assert len(client.created) == 2
    assert "input_schema" in client.created[0]
    assert "input_schema" not in client.created[1]
    assert client.created[1]["metadata"]["schema_enforced"] is False


def test_sync_does_not_hide_unrelated_bad_request():
    class BadClient(_FakeClient):
        def create_dataset(self, **kwargs):
            raise _HttpError(400, {"error": "bad name"})

    with pytest.raises(_HttpError):
        sync_dataset([_case()], suite="smoke", client=BadClient())


def test_existing_dataset_is_upserted_without_create():
    client = _FakeClient(exists=True)
    result = sync_dataset([_case()], suite="smoke", client=client)
    assert not result.created
    assert not result.schema_enforced
    assert client.created == []
    assert len(client.items) == 1


def test_task_error_vetoes_otherwise_passing_scores():
    scores = _apply_task_error_veto(
        {"answer_correct": 1.0, "task_success": 1.0, "eval_pass": 1.0},
        "RateLimitError: 429",
    )
    assert scores == {"answer_correct": 1.0, "task_success": 0.0, "eval_pass": 0.0}


def test_no_task_error_preserves_scores():
    original = {"task_success": 1.0, "eval_pass": 1.0}
    assert _apply_task_error_veto(original, None) == original
