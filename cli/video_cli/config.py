"""Configuration management for video-cli.

Supports multiple named profiles (e.g. local + prod) in a single config file,
so different environments keep their own base_url and token side by side::

    {
      "current_profile": "prod",
      "profiles": {
        "local": {"base_url": "http://localhost:5022", "token": "..."},
        "prod":  {"base_url": "https://oneclick.video", "token": "..."}
      }
    }

Legacy flat configs ({"base_url": ..., "token": ...}) are still read and
written in place for backward compatibility.
"""
import json
from pathlib import Path

CONFIG_DIR = Path.home() / ".video-cli"
CONFIG_FILE = CONFIG_DIR / "config.json"

DEFAULT_BASE_URL = "http://localhost:5022"
DEFAULT_PROFILE = "default"

# Convenience defaults so `config use <name>` knows well-known environments.
KNOWN_PROFILE_URLS = {
    "local": "http://localhost:5022",
    "prod": "https://oneclick.video",
}


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


def _is_profile_format(config: dict) -> bool:
    return isinstance(config.get("profiles"), dict)


def _resolve_profile_name(config: dict, profile: str | None) -> str:
    return profile or config.get("current_profile") or DEFAULT_PROFILE


def _to_profile_format(config: dict) -> dict:
    """Migrate a legacy flat config (or empty) into profile format in place."""
    if _is_profile_format(config):
        return config
    legacy = {}
    if config.get("base_url"):
        legacy["base_url"] = config["base_url"]
    if config.get("token"):
        legacy["token"] = config["token"]
    name = config.get("current_profile") or DEFAULT_PROFILE
    profiles = {name: legacy} if legacy else {}
    return {"current_profile": name, "profiles": profiles}


# ---- profile management --------------------------------------------------

def get_current_profile() -> str:
    config = load_config()
    if _is_profile_format(config):
        return config.get("current_profile") or DEFAULT_PROFILE
    return DEFAULT_PROFILE


def list_profiles() -> dict:
    """Return {name: {base_url, token}} for all known profiles."""
    config = load_config()
    if _is_profile_format(config):
        return config.get("profiles", {})
    flat = {}
    if config.get("base_url"):
        flat["base_url"] = config["base_url"]
    if config.get("token"):
        flat["token"] = config["token"]
    return {DEFAULT_PROFILE: flat} if flat else {}


def use_profile(name: str, base_url: str | None = None):
    """Switch the active profile, creating it if necessary."""
    config = _to_profile_format(load_config())
    profiles = config.setdefault("profiles", {})
    prof = profiles.setdefault(name, {})
    if base_url:
        prof["base_url"] = base_url
    elif "base_url" not in prof and name in KNOWN_PROFILE_URLS:
        prof["base_url"] = KNOWN_PROFILE_URLS[name]
    config["current_profile"] = name
    save_config(config)


# ---- token / base_url accessors (profile-aware) --------------------------

def get_token(profile: str | None = None) -> str | None:
    config = load_config()
    if _is_profile_format(config):
        name = _resolve_profile_name(config, profile)
        return config.get("profiles", {}).get(name, {}).get("token")
    return config.get("token")


def set_token(token: str, profile: str | None = None):
    config = _to_profile_format(load_config())
    name = _resolve_profile_name(config, profile)
    config.setdefault("profiles", {}).setdefault(name, {})["token"] = token
    config.setdefault("current_profile", name)
    save_config(config)


def clear_token(profile: str | None = None):
    config = load_config()
    if not config:
        return
    if _is_profile_format(config):
        name = _resolve_profile_name(config, profile)
        if name in config.get("profiles", {}):
            config["profiles"][name].pop("token", None)
        save_config(config)
        return
    # legacy flat: drop token in place, preserving the rest
    config.pop("token", None)
    if config:
        save_config(config)
    else:
        clear_config()


def get_base_url(profile: str | None = None) -> str:
    config = load_config()
    if _is_profile_format(config):
        name = _resolve_profile_name(config, profile)
        return config.get("profiles", {}).get(name, {}).get("base_url") or DEFAULT_BASE_URL
    return config.get("base_url", DEFAULT_BASE_URL)


def set_base_url(url: str, profile: str | None = None):
    config = _to_profile_format(load_config())
    name = _resolve_profile_name(config, profile)
    config.setdefault("profiles", {}).setdefault(name, {})["base_url"] = url
    config.setdefault("current_profile", name)
    save_config(config)


def clear_config():
    if CONFIG_FILE.exists():
        CONFIG_FILE.unlink()
