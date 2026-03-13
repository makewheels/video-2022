from click.testing import CliRunner
from video_cli.main import cli


class TestCLIHelp:
    def setup_method(self):
        self.runner = CliRunner()

    def test_main_help(self):
        result = self.runner.invoke(cli, ["--help"])
        assert result.exit_code == 0
        assert "video-cli" in result.output
        assert "auth" in result.output
        assert "video" in result.output

    def test_version(self):
        result = self.runner.invoke(cli, ["--version"])
        assert result.exit_code == 0
        assert "1.0.0" in result.output

    def test_auth_help(self):
        result = self.runner.invoke(cli, ["auth", "--help"])
        assert result.exit_code == 0
        assert "login" in result.output
        assert "logout" in result.output
        assert "me" in result.output

    def test_video_help(self):
        result = self.runner.invoke(cli, ["video", "--help"])
        assert result.exit_code == 0
        assert "list" in result.output
        assert "create" in result.output
        assert "detail" in result.output
        assert "update" in result.output
        assert "delete" in result.output
        assert "status" in result.output
        assert "download-url" in result.output

    def test_comment_help(self):
        result = self.runner.invoke(cli, ["comment", "--help"])
        assert result.exit_code == 0
        assert "add" in result.output
        assert "list" in result.output
        assert "delete" in result.output

    def test_like_help(self):
        result = self.runner.invoke(cli, ["like", "--help"])
        assert result.exit_code == 0
        assert "like" in result.output
        assert "dislike" in result.output
        assert "status" in result.output

    def test_playlist_help(self):
        result = self.runner.invoke(cli, ["playlist", "--help"])
        assert result.exit_code == 0
        assert "create" in result.output
        assert "list" in result.output
        assert "delete" in result.output

    def test_youtube_help(self):
        result = self.runner.invoke(cli, ["youtube", "--help"])
        assert result.exit_code == 0
        assert "info" in result.output
        assert "transfer" in result.output
        assert "extension" in result.output

    def test_stats_help(self):
        result = self.runner.invoke(cli, ["stats", "--help"])
        assert result.exit_code == 0
        assert "traffic" in result.output
        assert "aggregate" in result.output

    def test_watch_help(self):
        result = self.runner.invoke(cli, ["watch", "--help"])
        assert result.exit_code == 0
        assert "info" in result.output
        assert "start" in result.output
        assert "heartbeat" in result.output
        assert "progress" in result.output
