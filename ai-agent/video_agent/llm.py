"""Minimal OpenAI-compatible planner client."""

from __future__ import annotations

import json
import os
from urllib import request

from .planner import Plan, plan_from_json


class OpenAICompatiblePlannerClient:
    def __init__(self) -> None:
        self.provider = os.getenv("VIDEO_AGENT_LLM_PROVIDER") or "openai-compatible"
        self.api_key, key_source = self._resolve_api_key()
        self.base_url = self._resolve_base_url(key_source)
        self.model = self._resolve_model(key_source)
        self.timeout = float(os.getenv("VIDEO_AGENT_LLM_TIMEOUT", "30"))
        if not self.api_key:
            raise RuntimeError("VIDEO_AGENT_LLM_API_KEY, OPENAI_API_KEY, or provider-specific API key is not set")
        if not self.model:
            raise RuntimeError("VIDEO_AGENT_LLM_MODEL, OPENAI_MODEL, or provider-specific model is not set")

    def plan(self, query: str) -> Plan:
        system = (
            "You route Chinese natural-language requests for a video platform. Return JSON only. "
            "Allowed intents: count_my_videos, first_uploaded_video, latest_uploaded_video, "
            "video_watch_count, video_status, video_traffic, watch_history, list_my_videos, "
            "upload_video, search_public_videos, comment_count, list_comments, list_playlists, "
            "unread_notification_count, list_notifications, like_status, share_stats, create_share. "
            "Fields: intent, args, confidence, needs_confirmation. "
            "For upload_video, extract file_path, title, visibility where possible. "
            "For video-specific queries, args.keyword is the video title or search keyword."
        )
        payload = {
            "model": self.model,
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": query},
            ],
            "temperature": 0,
        }
        req = request.Request(
            f"{self.base_url}/chat/completions",
            data=json.dumps(payload).encode("utf-8"),
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {self.api_key}",
            },
            method="POST",
        )
        with request.urlopen(req, timeout=self.timeout) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        content = data["choices"][0]["message"]["content"]
        plan = plan_from_json(_strip_code_fence(content))
        plan.source = f"llm:{self.provider}"
        return plan

    def _resolve_api_key(self) -> tuple[str | None, str | None]:
        if os.getenv("VIDEO_AGENT_LLM_API_KEY"):
            return os.getenv("VIDEO_AGENT_LLM_API_KEY"), "generic"
        if os.getenv("OPENAI_API_KEY"):
            return os.getenv("OPENAI_API_KEY"), "openai"
        if os.getenv("MINIMAX_API_KEY"):
            return os.getenv("MINIMAX_API_KEY"), "minimax"
        return None, None

    def _resolve_base_url(self, key_source: str | None) -> str:
        explicit = os.getenv("VIDEO_AGENT_LLM_BASE_URL")
        if explicit:
            return explicit.rstrip("/")
        if os.getenv("OPENAI_BASE_URL"):
            return os.getenv("OPENAI_BASE_URL", "").rstrip("/")
        if os.getenv("MINIMAX_BASE_URL"):
            return os.getenv("MINIMAX_BASE_URL", "").rstrip("/")
        if key_source == "openai":
            return "https://api.openai.com/v1"
        if key_source == "minimax":
            return "https://api.minimaxi.com/v1"
        raise RuntimeError("VIDEO_AGENT_LLM_BASE_URL is required for generic LLM providers")

    def _resolve_model(self, key_source: str | None) -> str | None:
        explicit = os.getenv("VIDEO_AGENT_LLM_MODEL")
        if explicit:
            return explicit
        if os.getenv("OPENAI_MODEL"):
            return os.getenv("OPENAI_MODEL")
        if os.getenv("MINIMAX_MODEL"):
            return os.getenv("MINIMAX_MODEL")
        if key_source == "minimax":
            return "MiniMax-M2.7"
        return None


def _strip_code_fence(text: str) -> str:
    t = text.strip()
    if t.startswith("```"):
        lines = t.splitlines()
        if lines and lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].startswith("```"):
            lines = lines[:-1]
        return "\n".join(lines).strip()
    return t
