import json
import responses
from click.testing import CliRunner
from unittest.mock import patch
from video_cli.main import cli


class TestWatchCommands:
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
    def test_watch_info(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/watchController/getWatchInfo",
            json={"code": 0, "message": "ok", "data": {"watchId": "w1", "videoId": "v1"}},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "watch", "info", "--watch-id", "w1"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["watchId"] == "w1"

    @responses.activate
    def test_watch_start(self):
        responses.add(
            responses.POST,
            "http://localhost:5022/playback/start",
            json={"code": 0, "message": "ok", "data": {"playbackId": "pb1"}},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "watch", "start", "--watch-id", "w1"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["success"] is True

    @responses.activate
    def test_watch_start_with_ids(self):
        responses.add(
            responses.POST,
            "http://localhost:5022/playback/start",
            json={"code": 0, "message": "ok", "data": {"playbackId": "pb1"}},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "watch", "start", "--watch-id", "w1", "--client-id", "c1", "--session-id", "s1"])
        assert result.exit_code == 0
        body = json.loads(responses.calls[0].request.body)
        assert body["clientId"] == "c1"
        assert body["sessionId"] == "s1"

    @responses.activate
    def test_watch_heartbeat(self):
        responses.add(
            responses.POST,
            "http://localhost:5022/playback/heartbeat",
            json={"code": 0, "message": "ok", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "watch", "heartbeat", "--watch-id", "w1", "--position", "30000"])
        assert result.exit_code == 0
        body = json.loads(responses.calls[0].request.body)
        assert body["position"] == 30000

    @responses.activate
    def test_watch_progress(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/progress/getProgress",
            json={"code": 0, "message": "ok", "data": {"position": 15000}},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "watch", "progress", "--watch-id", "w1"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["position"] == 15000

    @responses.activate
    def test_watch_info_api_error(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/watchController/getWatchInfo",
            json={"code": 1, "message": "not found", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "watch", "info", "--watch-id", "invalid"])
        assert result.exit_code != 0

    @responses.activate
    def test_watch_history(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/watchHistory/getMyHistory",
            json={"code": 0, "message": "ok", "data": {"list": [{"videoId": "v1", "title": "Test", "watchTime": "2024-01-01"}], "total": 1, "page": 0, "pageSize": 20}},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "watch", "history"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["total"] == 1
        assert len(data["list"]) == 1

    @responses.activate
    def test_watch_history_pagination(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/watchHistory/getMyHistory",
            json={"code": 0, "message": "ok", "data": {"list": [], "total": 0, "page": 1, "pageSize": 10}},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "watch", "history", "--page", "1", "--page-size", "10"])
        assert result.exit_code == 0
        assert "page=1" in responses.calls[0].request.url
        assert "pageSize=10" in responses.calls[0].request.url

    @responses.activate
    def test_watch_clear_history(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/watchHistory/clear",
            json={"code": 0, "message": "ok", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "watch", "clear-history"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["success"] is True

    @responses.activate
    def test_watch_history_api_error(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/watchHistory/getMyHistory",
            json={"code": 1, "message": "unauthorized", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "watch", "history"])
        assert result.exit_code != 0
