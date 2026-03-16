"""Tests for rate-limit-status CLI command."""
import json
import pytest
from unittest.mock import patch, MagicMock
from click.testing import CliRunner
from video_cli.main import cli


def api_response(data=None, code=0, message="success"):
    return {"code": code, "message": message, "data": data}


@pytest.fixture
def runner():
    return CliRunner()


def test_rate_limit_status_help(runner):
    result = runner.invoke(cli, ["developer", "rate-limit-status", "--help"])
    assert result.exit_code == 0
    assert "rate limit" in result.output.lower()


@patch("video_cli.client.requests")
def test_rate_limit_status_json(mock_requests, runner):
    status_data = {
        "allowed": True,
        "minuteLimit": 60,
        "minuteRemaining": 55,
        "dayLimit": 10000,
        "dayRemaining": 9990,
        "resetTime": 1700000000,
        "retryAfter": 0,
    }
    mock_resp = MagicMock()
    mock_resp.status_code = 200
    mock_resp.json.return_value = api_response(status_data)
    mock_resp.raise_for_status = MagicMock()
    mock_requests.get.return_value = mock_resp

    result = runner.invoke(cli, ["developer", "rate-limit-status"])
    assert result.exit_code == 0
    output = json.loads(result.output)
    assert output["minuteLimit"] == 60
    assert output["dayLimit"] == 10000
    assert output["minuteRemaining"] == 55


@patch("video_cli.client.requests")
def test_rate_limit_status_table(mock_requests, runner):
    status_data = {
        "allowed": True,
        "minuteLimit": 60,
        "minuteRemaining": 58,
        "dayLimit": 10000,
        "dayRemaining": 9999,
        "resetTime": 1700000000,
        "retryAfter": 0,
    }
    mock_resp = MagicMock()
    mock_resp.status_code = 200
    mock_resp.json.return_value = api_response(status_data)
    mock_resp.raise_for_status = MagicMock()
    mock_requests.get.return_value = mock_resp

    result = runner.invoke(cli, ["--output", "table", "developer", "rate-limit-status"])
    assert result.exit_code == 0
    assert "60" in result.output
