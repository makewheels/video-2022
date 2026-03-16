import json
import responses
from click.testing import CliRunner
from unittest.mock import patch
from video_cli.main import cli


class TestApiCommands:
    def setup_method(self):
        self.runner = CliRunner()
        self.patches = [
            patch("video_cli.client.get_token", return_value="t"),
            patch("video_cli.client.get_base_url", return_value="http://localhost:5022"),
        ]
        for p in self.patches:
            p.start()

    def teardown_method(self):
        for p in self.patches:
            p.stop()

    @responses.activate
    def test_rate_limit_status(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/api/v1/rateLimit/status",
            json={
                "code": 0,
                "message": "ok",
                "data": {
                    "allowed": True,
                    "minuteLimit": 60,
                    "minuteRemaining": 55,
                    "resetTime": 1700000060,
                    "dayLimit": 10000,
                    "dayRemaining": 9990,
                    "retryAfter": 0,
                },
            },
            status=200,
        )
        result = self.runner.invoke(
            cli, ["--token", "t", "api", "rate-limit-status"]
        )
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["minuteLimit"] == 60
        assert data["dayLimit"] == 10000
        assert data["allowed"] is True

    @responses.activate
    def test_rate_limit_status_table_format(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/api/v1/rateLimit/status",
            json={
                "code": 0,
                "message": "ok",
                "data": {
                    "allowed": True,
                    "minuteLimit": 60,
                    "minuteRemaining": 55,
                    "resetTime": 1700000060,
                    "dayLimit": 10000,
                    "dayRemaining": 9990,
                    "retryAfter": 0,
                },
            },
            status=200,
        )
        result = self.runner.invoke(
            cli, ["--token", "t", "--output", "table", "api", "rate-limit-status"]
        )
        assert result.exit_code == 0
        assert "minuteLimit" in result.output

    @responses.activate
    def test_rate_limit_status_api_error(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/api/v1/rateLimit/status",
            json={"code": 1, "message": "unauthorized", "data": None},
            status=200,
        )
        result = self.runner.invoke(
            cli, ["--token", "t", "api", "rate-limit-status"]
        )
        assert result.exit_code != 0
