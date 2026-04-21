import json
from click.testing import CliRunner
from unittest.mock import patch
from video_cli.main import cli


class TestConfigCommands:
    def setup_method(self):
        self.runner = CliRunner()

    def test_show_config_masks_token(self):
        with patch("video_cli.commands.config.get_base_url", return_value="http://localhost:5022"), \
             patch("video_cli.commands.config.get_token", return_value="token-12345678"), \
             patch("video_cli.commands.config.CONFIG_FILE", "/tmp/video-cli.json"):
            result = self.runner.invoke(cli, ["config", "show"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["baseUrl"] == "http://localhost:5022"
        assert data["hasToken"] is True
        assert data["token"] == "toke...5678"

    def test_show_config_can_show_full_token(self):
        with patch("video_cli.commands.config.get_base_url", return_value="http://localhost:5022"), \
             patch("video_cli.commands.config.get_token", return_value="token-12345678"), \
             patch("video_cli.commands.config.CONFIG_FILE", "/tmp/video-cli.json"):
            result = self.runner.invoke(cli, ["config", "show", "--show-token"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["token"] == "token-12345678"

    def test_set_base_url(self):
        with patch("video_cli.commands.config.set_base_url") as mock_set:
            result = self.runner.invoke(cli, ["config", "set-base-url", "http://example.com"])
        assert result.exit_code == 0
        mock_set.assert_called_once_with("http://example.com")

    def test_set_token(self):
        with patch("video_cli.commands.config.set_token") as mock_set:
            result = self.runner.invoke(cli, ["config", "set-token", "abc123"])
        assert result.exit_code == 0
        mock_set.assert_called_once_with("abc123")

    def test_clear_token(self):
        with patch("video_cli.commands.config.clear_token") as mock_clear:
            result = self.runner.invoke(cli, ["config", "clear-token"])
        assert result.exit_code == 0
        mock_clear.assert_called_once()

    def test_clear(self):
        with patch("video_cli.commands.config.clear_config") as mock_clear:
            result = self.runner.invoke(cli, ["config", "clear"])
        assert result.exit_code == 0
        mock_clear.assert_called_once()
