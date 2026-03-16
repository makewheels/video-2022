"""Tests for notification CLI commands."""
import json
import pytest
from unittest.mock import patch, MagicMock
from click.testing import CliRunner
from video_cli.main import cli


@pytest.fixture
def runner():
    return CliRunner()


@patch("video_cli.commands.notification.get")
def test_notification_list(mock_get, runner):
    """notification list should call API and print result."""
    mock_get.return_value = {
        "list": [
            {
                "id": "n_001",
                "type": "COMMENT_REPLY",
                "content": "回复了你的评论",
                "read": False,
                "createTime": "2025-01-01T00:00:00",
            }
        ],
        "total": 1,
        "totalPages": 1,
        "currentPage": 0,
        "pageSize": 20,
    }
    result = runner.invoke(cli, ["notification", "list"])
    assert result.exit_code == 0
    data = json.loads(result.output)
    assert "list" in data
    assert len(data["list"]) == 1
    mock_get.assert_called_once()


@patch("video_cli.commands.notification.post")
def test_notification_read(mock_post, runner):
    """notification read should mark notification as read."""
    mock_post.return_value = None
    result = runner.invoke(cli, ["notification", "read", "n_001"])
    assert result.exit_code == 0
    output = json.loads(result.output)
    assert output["success"] is True
    mock_post.assert_called_once_with(
        "/notification/markAsRead",
        {"notificationId": "n_001"},
        base_url=None,
        token=None,
    )


@patch("video_cli.commands.notification.post")
def test_notification_read_all(mock_post, runner):
    """notification read-all should mark all as read."""
    mock_post.return_value = None
    result = runner.invoke(cli, ["notification", "read-all"])
    assert result.exit_code == 0
    output = json.loads(result.output)
    assert output["success"] is True
    mock_post.assert_called_once_with(
        "/notification/markAllAsRead",
        {},
        base_url=None,
        token=None,
    )


@patch("video_cli.commands.notification.get")
def test_notification_unread_count(mock_get, runner):
    """notification unread-count should return count."""
    mock_get.return_value = 5
    result = runner.invoke(cli, ["notification", "unread-count"])
    assert result.exit_code == 0
    assert "5" in result.output
    mock_get.assert_called_once()
