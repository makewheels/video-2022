import json
import responses
from click.testing import CliRunner
from unittest.mock import patch
from video_cli.main import cli


class TestShareCommands:
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
    def test_share_create(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/share/create",
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "id": "abc123",
                    "videoId": "v1",
                    "shortCode": "ab12cd34",
                    "createdBy": "user1",
                    "clickCount": 0,
                    "createTime": "2024-01-01T00:00:00",
                },
            },
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "share", "create", "--video-id", "v1"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["shortCode"] == "ab12cd34"
        assert data["videoId"] == "v1"

    @responses.activate
    def test_share_create_table_format(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/share/create",
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "shortCode": "ab12cd34",
                    "videoId": "v1",
                    "clickCount": 0,
                },
            },
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "--output", "table", "share", "create", "--video-id", "v1"])
        assert result.exit_code == 0
        assert "ab12cd34" in result.output

    @responses.activate
    def test_share_stats(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/share/stats",
            json={
                "code": 0,
                "message": "success",
                "data": {
                    "shortCode": "ab12cd34",
                    "videoId": "v1",
                    "clickCount": 42,
                    "lastReferrer": "https://twitter.com",
                    "createTime": "2024-01-01T00:00:00",
                },
            },
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "share", "stats", "--short-code", "ab12cd34"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["clickCount"] == 42
        assert data["shortCode"] == "ab12cd34"

    @responses.activate
    def test_share_stats_not_found(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/share/stats",
            json={"code": 1, "message": "分享链接不存在", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "share", "stats", "--short-code", "nosuchcode"])
        assert result.exit_code != 0

    @responses.activate
    def test_share_create_api_error(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/share/create",
            json={"code": 22, "message": "视频不存在", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "share", "create", "--video-id", "nonexistent"])
        assert result.exit_code != 0
