import json
import responses
from click.testing import CliRunner
from unittest.mock import patch
from video_cli.main import cli


class TestYouTubeCommands:
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
    def test_youtube_info(self):
        responses.add(
            responses.GET,
            "https://youtube.videoplus.top:5030/youtube/getVideoInfo",
            json={"code": 0, "message": "ok", "data": {"title": "Test Video", "duration": 120}},
            status=200,
        )
        result = self.runner.invoke(cli, ["youtube", "info", "--youtube-id", "dQw4w9WgXcQ"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["title"] == "Test Video"
        assert data["duration"] == 120

    @responses.activate
    def test_youtube_transfer(self):
        responses.add(
            responses.POST,
            "http://localhost:5022/youtube/transferVideo",
            json={"code": 0, "message": "ok", "data": {"videoId": "v1"}},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "youtube", "transfer", "--youtube-id", "dQw4w9WgXcQ"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["success"] is True

    @responses.activate
    def test_youtube_extension(self):
        responses.add(
            responses.GET,
            "https://youtube.videoplus.top:5030/youtube/getFileExtension",
            json={"code": 0, "message": "ok", "data": {"extension": "mp4"}},
            status=200,
        )
        result = self.runner.invoke(cli, ["youtube", "extension", "--youtube-id", "dQw4w9WgXcQ"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["extension"] == "mp4"

    @responses.activate
    def test_youtube_info_api_error(self):
        responses.add(
            responses.GET,
            "https://youtube.videoplus.top:5030/youtube/getVideoInfo",
            json={"code": 1, "message": "video not found", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["youtube", "info", "--youtube-id", "invalid"])
        assert result.exit_code != 0
