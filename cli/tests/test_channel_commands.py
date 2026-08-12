import json
import responses
from click.testing import CliRunner
from unittest.mock import patch
from video_cli.main import cli


class TestChannelCommands:
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
    def test_channel_subscribe(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/subscription/subscribe",
            json={"code": 0, "message": "ok", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "channel", "subscribe", "--user-id", "u1"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["success"] is True
        assert "channelUserId=u1" in responses.calls[0].request.url

    @responses.activate
    def test_channel_unsubscribe(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/subscription/unsubscribe",
            json={"code": 0, "message": "ok", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "channel", "unsubscribe", "--user-id", "u1"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["success"] is True
        assert "channelUserId=u1" in responses.calls[0].request.url

    @responses.activate
    def test_channel_subscriptions(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/subscription/getMySubscriptions",
            json={"code": 0, "message": "ok", "data": ["u1", "u2"]},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "channel", "subscriptions", "--skip", "0", "--limit", "5"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data == ["u1", "u2"]
        assert "skip=0" in responses.calls[0].request.url
        assert "limit=5" in responses.calls[0].request.url

    @responses.activate
    def test_channel_get(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/user/getChannel",
            json={"code": 0, "message": "ok", "data": {"userId": "u1", "nickname": "频道主", "subscriberCount": 10, "videoCount": 3, "isSubscribed": False}},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "channel", "get", "--user-id", "u1"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["userId"] == "u1"
        assert data["subscriberCount"] == 10

    @responses.activate
    def test_channel_subscribe_api_error(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/subscription/subscribe",
            json={"code": 1, "message": "already subscribed", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "channel", "subscribe", "--user-id", "u1"])
        assert result.exit_code != 0
