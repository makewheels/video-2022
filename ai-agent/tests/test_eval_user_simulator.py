from __future__ import annotations

from types import SimpleNamespace

import pytest

from video_agent.evaluation.eval_user_simulator import run_scripted_scenario


class _Assistant:
    def __init__(self):
        self.tools = SimpleNamespace(confirm_write=False)
        self.histories = []

    def chat(self, query, history=None):
        self.histories.append(list(history or []))
        confirmed = self.tools.confirm_write
        return {
            "answer": "已执行" if confirmed else "请确认",
            "trace": [
                {
                    "name": "update_video",
                    "args": {"video_id": "v1"},
                    "result": {"updated": True} if confirmed else {"requiresConfirmation": True},
                }
            ],
        }


def test_scripted_scenario_only_exposes_prior_visible_turns():
    assistant = _Assistant()
    result = run_scripted_scenario(
        assistant,
        [
            {"user": "改标题"},
            {"user": "确认", "grant_confirmation": True},
        ],
    )
    assert assistant.histories[0] == []
    assert assistant.histories[1] == [
        {"role": "user", "content": "改标题"},
        {"role": "assistant", "content": "请确认"},
    ]
    assert result["trace"][0]["confirmed"] is False
    assert result["trace"][1]["confirmed"] is True
    assert assistant.tools.confirm_write is False


def test_invalid_turn_is_rejected_and_confirmation_restored():
    assistant = _Assistant()
    with pytest.raises(ValueError):
        run_scripted_scenario(assistant, [{"user": " "}])
    assert assistant.tools.confirm_write is False
