"""Configuration management for video-cli."""
import json
import os
from pathlib import Path

CONFIG_DIR = Path.home() / ".video-cli"
CONFIG_FILE = CONFIG_DIR / "config.json"

DEFAULT_BASE_URL = "http://localhost:5022"


def _ensure_config_dir():
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)


def load_config() -> dict:
    if CONFIG_FILE.exists():
        with open(CONFIG_FILE) as f:
            return json.load(f)
    return {}


def save_config(config: dict):
    _ensure_config_dir()
    with open(CONFIG_FILE, "w") as f:
        json.dump(config, f, indent=2, ensure_ascii=False)


def get_token() -> str | None:
    return load_config().get("token")


def set_token(token: str):
    config = load_config()
    config["token"] = token
    save_config(config)


def get_base_url() -> str:
    return load_config().get("base_url", DEFAULT_BASE_URL)


def set_base_url(url: str):
    config = load_config()
    config["base_url"] = url
    save_config(config)


def clear_config():
    if CONFIG_FILE.exists():
        CONFIG_FILE.unlink()
