import json
import responses
from click.testing import CliRunner
from unittest.mock import patch
from video_cli.main import cli


class TestPlaylistCommands:
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
    def test_playlist_create(self):
        responses.add(
            responses.POST,
            "http://localhost:5022/playlist/createPlaylist",
            json={"code": 0, "message": "ok", "data": {"id": "p1"}},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "playlist", "create", "--title", "My List"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["success"] is True

    @responses.activate
    def test_playlist_list(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/playlist/getMyPlaylistByPage",
            json={"code": 0, "message": "ok", "data": [{"id": "p1", "title": "List 1"}]},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "playlist", "list"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data[0]["id"] == "p1"

    @responses.activate
    def test_playlist_detail(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/playlist/getPlaylistById",
            json={"code": 0, "message": "ok", "data": {"id": "p1", "title": "My List", "items": []}},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "playlist", "detail", "--id", "p1"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["id"] == "p1"

    @responses.activate
    def test_playlist_add_item(self):
        responses.add(
            responses.POST,
            "http://localhost:5022/playlist/addPlaylistItem",
            json={"code": 0, "message": "ok", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "playlist", "add-item", "--playlist-id", "p1", "--video-id", "v1"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["success"] is True

    @responses.activate
    def test_playlist_delete_item(self):
        responses.add(
            responses.POST,
            "http://localhost:5022/playlist/deletePlaylistItem",
            json={"code": 0, "message": "ok", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "playlist", "delete-item", "--playlist-id", "p1", "--video-id", "v1"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["success"] is True

    @responses.activate
    def test_playlist_delete(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/playlist/deletePlaylist",
            json={"code": 0, "message": "ok", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "playlist", "delete", "--id", "p1"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["success"] is True

    @responses.activate
    def test_playlist_recover(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/playlist/recoverPlaylist",
            json={"code": 0, "message": "ok", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "playlist", "recover", "--id", "p1"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["success"] is True

    @responses.activate
    def test_playlist_update(self):
        responses.add(
            responses.POST,
            "http://localhost:5022/playlist/updatePlaylist",
            json={"code": 0, "message": "ok", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "playlist", "update", "--id", "p1", "--title", "Updated"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["success"] is True
        body = json.loads(responses.calls[0].request.body)
        assert body["title"] == "Updated"
        assert body["playlistId"] == "p1"

    @responses.activate
    def test_playlist_create_api_error(self):
        responses.add(
            responses.POST,
            "http://localhost:5022/playlist/createPlaylist",
            json={"code": 1, "message": "quota exceeded", "data": None},
            status=200,
        )
        result = self.runner.invoke(cli, ["--token", "t", "playlist", "create", "--title", "Test"])
        assert result.exit_code != 0
