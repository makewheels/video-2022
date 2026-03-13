import pytest
from unittest.mock import patch
from click.testing import CliRunner
from video_cli.main import cli


@pytest.fixture
def runner():
    return CliRunner()


@pytest.fixture
def mock_config(tmp_path):
    config_file = tmp_path / "config.json"
    config_dir = tmp_path
    with patch("video_cli.config.CONFIG_DIR", config_dir), \
         patch("video_cli.config.CONFIG_FILE", config_file):
        yield config_file


def api_response(data=None, code=0, message="success"):
    """Helper to create a standard API response."""
    return {"code": code, "message": message, "data": data}
