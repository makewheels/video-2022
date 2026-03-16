"""Tests for developer commands."""
import json
import pytest
from unittest.mock import patch, MagicMock
from click.testing import CliRunner
from video_cli.main import cli

try:
    from tests.conftest import api_response
except ImportError:
    def api_response(data=None, code=0, message="success"):
        return {"code": code, "message": message, "data": data}


@pytest.fixture
def runner():
    return CliRunner()


@patch("video_cli.client.requests")
def test_create_app(mock_requests, runner):
    app_data = {
        "appId": "abc123",
        "appName": "TestApp",
        "appSecret": "s" * 32,
        "status": "active",
    }
    mock_resp = MagicMock()
    mock_resp.status_code = 200
    mock_resp.json.return_value = api_response(app_data)
    mock_resp.raise_for_status = MagicMock()
    mock_requests.post.return_value = mock_resp

    result = runner.invoke(cli, ["developer", "create-app", "TestApp"])
    assert result.exit_code == 0
    output = json.loads(result.output)
    assert output["appId"] == "abc123"
    assert output["appName"] == "TestApp"


@patch("video_cli.client.requests")
def test_list_apps(mock_requests, runner):
    apps = [
        {"appId": "abc123", "appName": "App1", "status": "active"},
        {"appId": "def456", "appName": "App2", "status": "active"},
    ]
    mock_resp = MagicMock()
    mock_resp.status_code = 200
    mock_resp.json.return_value = api_response(apps)
    mock_resp.raise_for_status = MagicMock()
    mock_requests.get.return_value = mock_resp

    result = runner.invoke(cli, ["developer", "list-apps"])
    assert result.exit_code == 0
    output = json.loads(result.output)
    assert isinstance(output, list)
    assert len(output) == 2


@patch("video_cli.client.requests")
def test_create_token(mock_requests, runner):
    token_data = {"token": "eyJhbGciOiJIUzI1NiJ9.test.sig"}
    mock_resp = MagicMock()
    mock_resp.status_code = 200
    mock_resp.json.return_value = api_response(token_data)
    mock_resp.raise_for_status = MagicMock()
    mock_requests.post.return_value = mock_resp

    result = runner.invoke(cli, ["developer", "create-token", "abc123", "secret123"])
    assert result.exit_code == 0
    output = json.loads(result.output)
    assert "token" in output


@patch("video_cli.client.requests")
def test_create_app_error(mock_requests, runner):
    mock_resp = MagicMock()
    mock_resp.status_code = 200
    mock_resp.json.return_value = api_response(code=1, message="Unauthorized")
    mock_resp.raise_for_status = MagicMock()
    mock_requests.post.return_value = mock_resp

    result = runner.invoke(cli, ["developer", "create-app", "TestApp"])
    assert result.exit_code != 0
