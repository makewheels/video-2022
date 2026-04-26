"""Configuration for the video agent — auto-loads .env from ai-agent/ directory."""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path


def _load_dotenv() -> None:
    """Load .env from ai-agent/ directory if it exists."""
    env_path = Path(__file__).resolve().parents[1] / ".env"
    if not env_path.exists():
        return
    with env_path.open(encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, val = line.partition("=")
            key, val = key.strip(), val.strip()
            if val and (val.startswith('"') or val.startswith("'")):
                val = val[1:-1]
            if key and val and key not in os.environ:
                os.environ[key] = val


_load_dotenv()

# ── well-known provider defaults ──────────────────────────────────
_PROVIDER_DEFAULTS: dict[str, dict[str, str]] = {
    "deepseek": {
        "base_url": "https://api.deepseek.com/v1",
        "model": "deepseek-chat",
    },
    "minimax": {
        "base_url": "https://api.minimaxi.com/v1",
        "model": "MiniMax-M2.7",
    },
    "openai": {
        "base_url": "https://api.openai.com/v1",
        "model": "gpt-4o",
    },
    "moonshot": {
        "base_url": "https://api.moonshot.cn/v1",
        "model": "moonshot-v1-8k",
    },
}


@dataclass
class AgentConfig:
    """Agent configuration resolved from env vars with sensible defaults."""

    # ── LLM ──
    provider: str = "deepseek"
    base_url: str = ""
    api_key: str = ""
    model: str = ""
    temperature: float = 0.0
    max_tokens: int = 4096
    timeout: float = 60.0

    # ── backend ──
    backend: str = "fixture"               # "fixture" | "cli"
    fixture_path: str = ""
    base_url_video: str = "http://localhost:5022"
    video_token: str = ""
    confirm_write: bool = False

    # ── paths ──
    project_root: str = field(default_factory=lambda: str(Path(__file__).resolve().parents[1]))

    def __post_init__(self) -> None:
        self._resolve()

    def _resolve(self) -> None:
        # provider
        self.provider = os.getenv("VIDEO_AGENT_PROVIDER") or os.getenv("VIDEO_AGENT_LLM_PROVIDER") or self.provider

        # api key
        self.api_key = (
            os.getenv("VIDEO_AGENT_LLM_API_KEY")
            or os.getenv("OPENAI_API_KEY")
            or os.getenv("DEEPSEEK_API_KEY")
            or os.getenv("MINIMAX_API_KEY")
            or ""
        )
        # try ANTHROPIC_AUTH_TOKEN as last resort (may work for DeepSeek Anthropic endpoint)
        if not self.api_key:
            self.api_key = os.getenv("ANTHROPIC_AUTH_TOKEN", "")

        # base_url
        self.base_url = os.getenv("VIDEO_AGENT_LLM_BASE_URL") or ""
        if not self.base_url:
            provider_defaults = _PROVIDER_DEFAULTS.get(self.provider, {})
            self.base_url = os.getenv("OPENAI_BASE_URL") or provider_defaults.get("base_url", "")

        # model
        self.model = os.getenv("VIDEO_AGENT_LLM_MODEL") or ""
        if not self.model:
            provider_defaults = _PROVIDER_DEFAULTS.get(self.provider, {})
            self.model = os.getenv("OPENAI_MODEL") or provider_defaults.get("model", "")

        # backend
        self.backend = os.getenv("VIDEO_AGENT_BACKEND") or self.backend
        self.fixture_path = os.getenv("VIDEO_AGENT_FIXTURE") or os.path.join(self.project_root, "fixtures", "videos.json")
        self.base_url_video = os.getenv("VIDEO_CLI_BASE_URL") or self.base_url_video
        self.video_token = os.getenv("VIDEO_CLI_TOKEN") or ""
        self.confirm_write = os.getenv("VIDEO_AGENT_CONFIRM_WRITE", "").lower() in ("1", "true", "yes")

        # numeric
        try:
            self.temperature = float(os.getenv("VIDEO_AGENT_TEMPERATURE", str(self.temperature)))
        except ValueError:
            pass
        try:
            self.max_tokens = int(os.getenv("VIDEO_AGENT_MAX_TOKENS", str(self.max_tokens)))
        except ValueError:
            pass
        try:
            self.timeout = float(os.getenv("VIDEO_AGENT_TIMEOUT", str(self.timeout)))
        except ValueError:
            pass


# module-level singleton
_config: AgentConfig | None = None


def get_config() -> AgentConfig:
    global _config
    if _config is None:
        _config = AgentConfig()
    return _config
