import json
import responses
from click.testing import CliRunner
from unittest.mock import patch
from video_cli.main import cli


class TestVideoCommands:
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
    def test_video_list(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/video/getMyVideoList",
            json={"code": 0, "message": "ok", "data": {"list": [{"id": "v1", "title": "Test"}], "total": 1}},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "video", "list"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["list"][0]["id"] == "v1"

    @responses.activate
    def test_video_list_with_keyword(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/video/getMyVideoList",
            json={"code": 0, "message": "ok", "data": {"list": [], "total": 0}},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "video", "list", "--keyword", "测试"])
        assert result.exit_code == 0
        assert "keyword" in responses.calls[0].request.url

    @responses.activate
    def test_video_list_with_pagination(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/video/getMyVideoList",
            json={"code": 0, "message": "ok", "data": {"list": [], "total": 0}},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "video", "list", "--skip", "10", "--limit", "5"])
        assert result.exit_code == 0
        assert "skip=10" in responses.calls[0].request.url
        assert "limit=5" in responses.calls[0].request.url

    @responses.activate
    def test_video_detail(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/video/getVideoDetail",
            json={"code": 0, "message": "ok", "data": {"id": "v1", "title": "Test Video", "status": "READY"}},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "video", "detail", "--id", "v1"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["id"] == "v1"
        assert data["title"] == "Test Video"

    @responses.activate
    def test_video_status(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/video/getVideoStatus",
            json={"code": 0, "message": "ok", "data": {"status": "TRANSCODING"}},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "video", "status", "--id", "v1"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["status"] == "TRANSCODING"

    @responses.activate
    def test_video_update(self):
        responses.add(
            responses.POST,
            "http://localhost:5022/video/updateInfo",
            json={"code": 0, "message": "ok", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "video", "update", "--id", "v1", "--title", "New Title"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["success"] is True
        body = json.loads(responses.calls[0].request.body)
        assert body["title"] == "New Title"
        assert body["id"] == "v1"

    @responses.activate
    def test_video_delete(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/video/delete",
            json={"code": 0, "message": "ok", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "video", "delete", "--id", "v1"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["success"] is True

    @responses.activate
    def test_video_create(self):
        responses.add(
            responses.POST,
            "http://localhost:5022/video/create",
            json={"code": 0, "message": "ok", "data": {"videoId": "new-v1", "uploadUrl": "https://oss.example.com"}},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "video", "create", "--file", "nonexistent.mp4"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["data"]["videoId"] == "new-v1"

    @responses.activate
    def test_video_download_url(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/video/getRawFileDownloadUrl",
            json={"code": 0, "message": "ok", "data": {"url": "https://cdn.example.com/raw.mp4"}},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "video", "download-url", "--id", "v1"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["url"] == "https://cdn.example.com/raw.mp4"

    @responses.activate
    def test_video_list_api_error(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/video/getMyVideoList",
            json={"code": 1, "message": "unauthorized", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "video", "list"])
        assert result.exit_code != 0

    @responses.activate
    def test_video_list_table_format(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/video/getMyVideoList",
            json={"code": 0, "message": "ok", "data": {"list": [{"id": "v1", "title": "T", "status": "READY", "watchCount": 5}], "total": 1}},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "--output", "table", "video", "list"])
        assert result.exit_code == 0
        assert "v1" in result.output
