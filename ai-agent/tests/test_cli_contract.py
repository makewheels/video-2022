"""契约测试：VideoTools 生成的每个 CLI 调用必须能被 video-cli 的真实 click 命令树解析。

背景：tools.py 手写 argv、cli/ 独立演进，两边曾悄悄漂移出 6 个一调就报错的断链
（--days vs --start/--end、缺 --client-id、--id vs 位置参数、--id vs --parent-id、
位置参数 vs --youtube-id）。本测试在不发网络请求的前提下把两类漂移钉死：
monkeypatch _run_cli 只捕获参数，再用 click 的 parse_args 对着真实命令树校验。
"""

from __future__ import annotations

import sys
from pathlib import Path

import click
import pytest

CLI_DIR = Path(__file__).resolve().parents[2] / "cli"
if str(CLI_DIR) not in sys.path:
    sys.path.insert(0, str(CLI_DIR))

from video_cli.main import cli as video_cli  # noqa: E402

from video_agent.tools import VideoTools, _extract_youtube_id  # noqa: E402

# 覆盖全部走 _run_cli 的工具（upload_video 直连 HTTP，不在此列）
CALLS: list[tuple[str, dict]] = [
    # video
    ("list_my_videos", {"keyword": "教程", "skip": 1, "limit": 5}),
    ("get_video_detail", {"video_id": "v1"}),
    ("get_video_status", {"video_id": "v1"}),
    ("get_video_traffic", {"video_id": "v1"}),
    ("update_video", {"video_id": "v1", "title": "新标题", "visibility": "PRIVATE"}),
    ("delete_video", {"video_id": "v1"}),
    ("get_video_download_url", {"video_id": "v1"}),
    # comment
    ("comment_count", {"video_id": "v1"}),
    ("list_comments", {"video_id": "v1", "skip": 0, "limit": 5}),
    ("add_comment", {"video_id": "v1", "content": "不错"}),
    ("delete_comment", {"comment_id": "c1"}),
    ("like_comment", {"comment_id": "c1"}),
    ("comment_replies", {"comment_id": "c1"}),
    # playlist
    ("list_playlists", {"skip": 0, "limit": 5}),
    ("get_playlist_detail", {"playlist_id": "p1"}),
    ("create_playlist", {"title": "学习"}),
    ("add_video_to_playlist", {"playlist_id": "p1", "video_id": "v1"}),
    ("remove_video_from_playlist", {"playlist_id": "p1", "video_id": "v1"}),
    ("delete_playlist", {"playlist_id": "p1"}),
    ("update_playlist", {"playlist_id": "p1", "title": "新名字"}),
    ("move_playlist_item", {"playlist_id": "p1", "video_id": "v1", "to_index": 0}),
    ("recover_playlist", {"playlist_id": "p1"}),
    # notification
    ("unread_notification_count", {}),
    ("list_notifications", {"page": 0, "page_size": 5}),
    ("mark_notification_read", {"notification_id": "n1"}),
    ("mark_all_notifications_read", {}),
    # like
    ("like_status", {"video_id": "v1"}),
    ("like_video", {"video_id": "v1"}),
    ("dislike_video", {"video_id": "v1"}),
    # share
    ("create_share", {"video_id": "v1"}),
    ("share_stats", {"short_code": "abc123"}),
    # watch
    ("watch_history", {"page": 0, "page_size": 5}),
    ("get_watch_progress", {"video_id": "v1", "client_id": "client-1"}),
    ("clear_watch_history", {}),
    # search / stats / auth / youtube
    ("search_public_videos", {"keyword": "美食", "category": "生活", "page": 0, "page_size": 5}),
    ("get_public_video_list", {"skip": 0, "limit": 5, "keyword": "美食"}),
    ("get_traffic_stats", {"days": 7}),
    ("get_my_info", {}),
    ("get_my_profile", {}),
    ("update_profile", {"nickname": "新昵称", "bio": "新简介"}),
    # channel / subscription
    ("subscribe_channel", {"channel_user_id": "u1"}),
    ("unsubscribe_channel", {"channel_user_id": "u1"}),
    ("get_my_subscriptions", {"skip": 0, "limit": 5}),
    ("get_channel", {"user_id": "u1"}),
    ("get_youtube_info", {"url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ"}),
    ("transfer_youtube", {"url": "https://youtu.be/dQw4w9WgXcQ"}),
    # resolve（经 list_my_videos 走 CLI）
    ("resolve_videos", {"keyword": "教程"}),
]


def _parse_argv(argv: list[str]) -> None:
    """沿 click 命令树解析 argv，任何未知选项/缺失必填/未知子命令都抛错。"""
    cmd = video_cli
    args = list(argv)
    ctx = click.Context(cmd)
    while isinstance(cmd, click.Group):
        name = args.pop(0)
        cmd = cmd.get_command(ctx, name)
        assert cmd is not None, f"video-cli 不存在子命令: {name}（完整 argv: {argv!r}）"
        ctx = click.Context(cmd, parent=ctx)
    try:
        cmd.parse_args(ctx, args)
    except click.UsageError as e:
        raise AssertionError(f"argv {argv!r} 无法被 video-cli 解析: {e}") from e


@pytest.mark.parametrize("method_name,kwargs", CALLS, ids=[c[0] for c in CALLS])
def test_cli_invocation_parses(method_name, kwargs, monkeypatch):
    tools = VideoTools(backend="cli", confirm_write=True)
    captured: list[list[str]] = []
    monkeypatch.setattr(tools, "_run_cli", lambda args: captured.append(args) or {})

    getattr(tools, method_name)(**kwargs)

    assert captured, f"{method_name} 没有产生 CLI 调用"
    for argv in captured:
        _parse_argv(argv)


def test_watch_progress_requires_client_id():
    tools = VideoTools(backend="cli")
    result = tools.get_watch_progress("v1")
    assert "error" in result


def test_resolve_videos_fixture_shape():
    result = VideoTools(backend="fixture").resolve_videos("AI")
    assert result["total"] >= 1
    assert {"videoId", "title", "status", "watchCount", "createTime"} <= set(result["candidates"][0])


@pytest.mark.parametrize(
    "value,expected",
    [
        ("dQw4w9WgXcQ", "dQw4w9WgXcQ"),
        ("https://www.youtube.com/watch?v=dQw4w9WgXcQ", "dQw4w9WgXcQ"),
        ("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s", "dQw4w9WgXcQ"),
        ("https://youtu.be/dQw4w9WgXcQ", "dQw4w9WgXcQ"),
        ("https://www.youtube.com/shorts/dQw4w9WgXcQ", "dQw4w9WgXcQ"),
        ("https://www.youtube.com/embed/dQw4w9WgXcQ", "dQw4w9WgXcQ"),
        ("  dQw4w9WgXcQ  ", "dQw4w9WgXcQ"),
        ("https://example.com/watch?v=dQw4w9WgXcQ", None),
        ("not-a-url", None),
        ("", None),
    ],
)
def test_extract_youtube_id(value, expected):
    assert _extract_youtube_id(value) == expected
