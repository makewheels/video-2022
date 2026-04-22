import json
import pytest
from unittest.mock import patch
from video_cli.config import load_config, save_config, get_token, set_token, clear_token, get_base_url, set_base_url, clear_config


class TestConfig:
    def test_load_config_no_file(self, tmp_path):
        with patch("video_cli.config.CONFIG_FILE", tmp_path / "nonexistent.json"):
            assert load_config() == {}

    def test_save_and_load_config(self, tmp_path):
        config_file = tmp_path / "config.json"
        with patch("video_cli.config.CONFIG_DIR", tmp_path), \
             patch("video_cli.config.CONFIG_FILE", config_file):
            save_config({"token": "abc123"})
            config = load_config()
            assert config["token"] == "abc123"

    def test_get_set_token(self, tmp_path):
        config_file = tmp_path / "config.json"
        with patch("video_cli.config.CONFIG_DIR", tmp_path), \
             patch("video_cli.config.CONFIG_FILE", config_file):
            assert get_token() is None
            set_token("mytoken")
            assert get_token() == "mytoken"

    def test_get_base_url_default(self, tmp_path):
        with patch("video_cli.config.CONFIG_FILE", tmp_path / "nonexistent.json"):
            assert get_base_url() == "http://localhost:5022"

    def test_set_base_url(self, tmp_path):
        config_file = tmp_path / "config.json"
        with patch("video_cli.config.CONFIG_DIR", tmp_path), \
             patch("video_cli.config.CONFIG_FILE", config_file):
            set_base_url("http://example.com:8080")
            assert get_base_url() == "http://example.com:8080"

    def test_clear_config(self, tmp_path):
        config_file = tmp_path / "config.json"
        with patch("video_cli.config.CONFIG_DIR", tmp_path), \
             patch("video_cli.config.CONFIG_FILE", config_file):
            save_config({"token": "abc"})
            assert config_file.exists()
            clear_config()
            assert not config_file.exists()

    def test_save_config_creates_dir(self, tmp_path):
        nested_dir = tmp_path / "sub" / "dir"
        config_file = nested_dir / "config.json"
        with patch("video_cli.config.CONFIG_DIR", nested_dir), \
             patch("video_cli.config.CONFIG_FILE", config_file):
            save_config({"key": "val"})
            assert config_file.exists()

    def test_clear_config_no_file(self, tmp_path):
        with patch("video_cli.config.CONFIG_FILE", tmp_path / "nonexistent.json"):
            clear_config()  # should not raise

    def test_clear_token_preserves_other_config(self, tmp_path):
        config_file = tmp_path / "config.json"
        with patch("video_cli.config.CONFIG_DIR", tmp_path), \
             patch("video_cli.config.CONFIG_FILE", config_file):
            save_config({"token": "abc", "base_url": "http://example.com"})
            clear_token()
            config = load_config()
            assert "token" not in config
            assert config["base_url"] == "http://example.com"
