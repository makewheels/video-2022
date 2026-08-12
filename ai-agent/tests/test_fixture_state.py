from __future__ import annotations

from video_agent.tools import VideoTools


def test_unconfirmed_write_does_not_change_fixture():
    tools = VideoTools(backend="fixture")
    before = tools.snapshot_state()
    result = tools.execute("delete_video", {"video_id": "v_food"})
    assert result["requiresConfirmation"] is True
    assert tools.snapshot_state() == before


def test_confirmed_write_changes_state_and_reset_restores_it():
    tools = VideoTools(backend="fixture", confirm_write=True)
    before = tools.snapshot_state()
    result = tools.execute("update_video", {"video_id": "v_food", "title": "周末美食"})
    assert result["updated"] is True
    after = tools.snapshot_state()
    assert after != before
    assert tools.get_video_detail("v_food")["title"] == "周末美食"
    tools.reset_fixture_state()
    assert tools.snapshot_state() == before


def test_confirmed_delete_removes_only_target_video():
    tools = VideoTools(backend="fixture", confirm_write=True)
    tools.execute("delete_video", {"video_id": "v_mid_ai"})
    ids = {video["id"] for video in tools.snapshot_state()["videos"]}
    assert "v_mid_ai" not in ids
    assert "v_new_ai" in ids


def test_representative_fixture_writes_have_observable_state():
    tools = VideoTools(backend="fixture", confirm_write=True)
    tools.execute("add_comment", {"video_id": "v_food", "content": "好吃"})
    assert any(comment["content"] == "好吃" for comment in tools.snapshot_state()["comments"]["v_food"])

    created = tools.execute("create_playlist", {"title": "新列表"})
    assert any(item["id"] == created["playlistId"] for item in tools.snapshot_state()["playlists"])

    tools.execute("mark_all_notifications_read", {})
    assert all(item["read"] for item in tools.snapshot_state()["notifications"])

    tools.execute("clear_watch_history", {})
    assert tools.snapshot_state()["watchHistory"]["total"] == 0

    tools.execute("like_video", {"video_id": "v_food"})
    assert tools.snapshot_state()["likeStatus"]["v_food"] == {"liked": True, "disliked": False}
