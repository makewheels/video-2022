import json
import responses
from click.testing import CliRunner
from unittest.mock import patch
from video_cli.main import cli


class TestCommentCommands:
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
    def test_comment_add(self):
        responses.add(
            responses.POST,
            "http://localhost:5022/comment/add",
            json={"code": 0, "message": "ok", "data": {"id": "c1"}},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "comment", "add", "--video-id", "v1", "--content", "好视频"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["success"] is True
        body = json.loads(responses.calls[0].request.body)
        assert body["content"] == "好视频"

    @responses.activate
    def test_comment_add_reply(self):
        responses.add(
            responses.POST,
            "http://localhost:5022/comment/add",
            json={"code": 0, "message": "ok", "data": {"id": "c2"}},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "comment", "add", "--video-id", "v1", "--content", "reply", "--parent-id", "c1"])
        assert result.exit_code == 0
        body = json.loads(responses.calls[0].request.body)
        assert body["parentId"] == "c1"

    @responses.activate
    def test_comment_list(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/comment/getByVideoId",
            json={"code": 0, "message": "ok", "data": [{"id": "c1", "content": "test"}]},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "comment", "list", "--video-id", "v1"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert len(data) == 1
        assert data[0]["id"] == "c1"

    @responses.activate
    def test_comment_count(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/comment/getCount",
            json={"code": 0, "message": "ok", "data": {"count": 42}},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "comment", "count", "--video-id", "v1"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["count"] == 42

    @responses.activate
    def test_comment_delete(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/comment/delete",
            json={"code": 0, "message": "ok", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "comment", "delete", "--id", "c1"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["success"] is True

    @responses.activate
    def test_comment_like(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/comment/like",
            json={"code": 0, "message": "ok", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "comment", "like", "--id", "c1"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["success"] is True

    @responses.activate
    def test_comment_replies(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/comment/getReplies",
            json={"code": 0, "message": "ok", "data": [{"id": "c2", "content": "reply"}]},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "comment", "replies", "--parent-id", "c1"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data[0]["id"] == "c2"

    @responses.activate
    def test_comment_add_api_error(self):
        responses.add(
            responses.POST,
            "http://localhost:5022/comment/add",
            json={"code": 1, "message": "forbidden", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "comment", "add", "--video-id", "v1", "--content", "test"])
        assert result.exit_code != 0
