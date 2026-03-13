import json
import responses
from click.testing import CliRunner
from unittest.mock import patch
from video_cli.main import cli


class TestAuthCommands:
    def setup_method(self):
        self.runner = CliRunner()

    @responses.activate
    def test_login_request_code(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/user/requestVerificationCode",
            json={"code": 0, "message": "ok", "data": None},
            status=200,
        )
        with patch("video_cli.client.get_token", return_value=None), \
             patch("video_cli.client.get_base_url", return_value="http://localhost:5022"):
            result = self.runner.invoke(cli, ["auth", "login", "--phone", "13800138000"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["success"] is True
        assert "Verification code sent" in data["message"]

    @responses.activate
    def test_login_submit_code(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/user/submitVerificationCode",
            json={"code": 0, "message": "ok", "data": {"token": "test-token-123", "phone": "13800138000"}},
            status=200,
        )
        with patch("video_cli.client.get_token", return_value=None), \
             patch("video_cli.client.get_base_url", return_value="http://localhost:5022"), \
             patch("video_cli.commands.auth.set_token") as mock_set:
            result = self.runner.invoke(cli, ["auth", "login", "--phone", "13800138000", "--code", "111"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["success"] is True
        mock_set.assert_called_once_with("test-token-123")

    @responses.activate
    def test_login_api_error(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/user/submitVerificationCode",
            json={"code": 1, "message": "验证码错误", "data": None},
            status=200,
        )
        with patch("video_cli.client.get_token", return_value=None), \
             patch("video_cli.client.get_base_url", return_value="http://localhost:5022"):
            result = self.runner.invoke(cli, ["auth", "login", "--phone", "13800138000", "--code", "999"])
        assert result.exit_code != 0

    @responses.activate
    def test_me(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/user/getUserByToken",
            json={"code": 0, "message": "ok", "data": {"id": "u1", "phone": "13800138000"}},
            status=200,
        )
        with patch("video_cli.client.get_token", return_value="fake-token"), \
             patch("video_cli.client.get_base_url", return_value="http://localhost:5022"), \
             patch("video_cli.commands.auth.get_token", return_value="fake-token"):
            result = self.runner.invoke(cli, ["--token", "fake-token", "auth", "me"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["id"] == "u1"

    def test_logout(self):
        with patch("video_cli.commands.auth.clear_config") as mock_clear:
            result = self.runner.invoke(cli, ["auth", "logout"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["success"] is True
        mock_clear.assert_called_once()

    @responses.activate
    def test_login_response_without_token(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/user/submitVerificationCode",
            json={"code": 0, "message": "ok", "data": {"status": "pending"}},
            status=200,
        )
        with patch("video_cli.client.get_token", return_value=None), \
             patch("video_cli.client.get_base_url", return_value="http://localhost:5022"):
            result = self.runner.invoke(cli, ["auth", "login", "--phone", "13800138000", "--code", "111"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["success"] is True

    def test_me_not_logged_in(self):
        with patch("video_cli.commands.auth.get_token", return_value=None), \
             patch("video_cli.client.get_token", return_value=None):
            result = self.runner.invoke(cli, ["auth", "me"])
        assert result.exit_code != 0
