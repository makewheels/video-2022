import json
import responses
from click.testing import CliRunner
from unittest.mock import patch
from video_cli.main import cli


class TestUserCommands:
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
    def test_user_profile(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/user/getMyProfile",
            json={"code": 0, "message": "ok", "data": {"id": "u1", "nickname": "我", "bio": "简介", "subscriberCount": 5}},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "user", "profile"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["id"] == "u1"
        assert data["nickname"] == "我"

    @responses.activate
    def test_user_update_profile(self):
        responses.add(
            responses.POST,
            "http://localhost:5022/user/updateProfile",
            json={"code": 0, "message": "ok", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "user", "update-profile", "--nickname", "新昵称", "--bio", "新简介"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["success"] is True
        body = json.loads(responses.calls[0].request.body)
        assert body == {"nickname": "新昵称", "bio": "新简介"}

    @responses.activate
    def test_user_update_profile_partial(self):
        responses.add(
            responses.POST,
            "http://localhost:5022/user/updateProfile",
            json={"code": 0, "message": "ok", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "user", "update-profile", "--nickname", "只改昵称"])
        assert result.exit_code == 0
        body = json.loads(responses.calls[0].request.body)
        assert body == {"nickname": "只改昵称"}

    def test_user_update_profile_no_fields(self):
        result = self.runner.invoke(cli, ["--token", "t", "user", "update-profile"])
        assert result.exit_code != 0

    @responses.activate
    def test_user_update_profile_api_error(self):
        responses.add(
            responses.POST,
            "http://localhost:5022/user/updateProfile",
            json={"code": 1, "message": "昵称不能超过30字", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "user", "update-profile", "--nickname", "x" * 31])
        assert result.exit_code != 0
