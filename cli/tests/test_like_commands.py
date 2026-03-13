import json
import responses
from click.testing import CliRunner
from unittest.mock import patch
from video_cli.main import cli


class TestLikeCommands:
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
    def test_like_video(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/videoLike/like",
            json={"code": 0, "message": "ok", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "like", "like", "--video-id", "v1"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["success"] is True

    @responses.activate
    def test_dislike_video(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/videoLike/dislike",
            json={"code": 0, "message": "ok", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "like", "dislike", "--video-id", "v1"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["success"] is True

    @responses.activate
    def test_like_status(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/videoLike/getStatus",
            json={"code": 0, "message": "ok", "data": {"liked": True, "disliked": False}},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "like", "status", "--video-id", "v1"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["liked"] is True
        assert data["disliked"] is False

    @responses.activate
    def test_like_api_error(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/videoLike/like",
            json={"code": 1, "message": "already liked", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "like", "like", "--video-id", "v1"])
        assert result.exit_code != 0
