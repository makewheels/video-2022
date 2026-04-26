"""Context management for long conversations with token optimization."""

from __future__ import annotations

import json
from typing import Any


class ContextManager:
    """Manages conversation context with token limits and summarization."""

    def __init__(self, max_tokens: int = 8000, system_prompt_tokens: int = 500):
        self.max_tokens = max_tokens
        self.system_prompt_tokens = system_prompt_tokens
        self.available_tokens = max_tokens - system_prompt_tokens

    def estimate_tokens(self, text: str) -> int:
        """Rough token estimation (1 token ≈ 4 chars for Chinese, 0.75 words for English)."""
        chinese_chars = sum(1 for c in text if '一' <= c <= '鿿')
        other_chars = len(text) - chinese_chars
        return int(chinese_chars / 4 + other_chars / 4)

    def estimate_message_tokens(self, message: dict[str, Any]) -> int:
        """Estimate tokens for a single message."""
        tokens = 4  # Base overhead per message
        content = message.get("content", "")
        if isinstance(content, str):
            tokens += self.estimate_tokens(content)
        elif isinstance(content, list):
            for item in content:
                if isinstance(item, dict) and "text" in item:
                    tokens += self.estimate_tokens(item["text"])

        # Tool calls
        if "tool_calls" in message:
            for tc in message["tool_calls"]:
                tokens += self.estimate_tokens(json.dumps(tc, ensure_ascii=False))

        # Tool results
        if message.get("role") == "tool":
            tokens += self.estimate_tokens(str(message.get("content", "")))

        return tokens

    def trim_messages(self, messages: list[dict[str, Any]], keep_recent: int = 4) -> list[dict[str, Any]]:
        """Trim messages to fit token limit, keeping system prompt and recent messages."""
        if not messages:
            return messages

        # Always keep system message
        system_msg = messages[0] if messages[0].get("role") == "system" else None
        conversation = messages[1:] if system_msg else messages

        # Calculate tokens
        total_tokens = sum(self.estimate_message_tokens(msg) for msg in conversation)

        if total_tokens <= self.available_tokens:
            return messages

        # Keep recent messages
        recent = conversation[-keep_recent:] if len(conversation) > keep_recent else conversation
        recent_tokens = sum(self.estimate_message_tokens(msg) for msg in recent)

        # If recent messages fit, add summary of older messages
        if recent_tokens <= self.available_tokens:
            older = conversation[:-keep_recent] if len(conversation) > keep_recent else []
            summary = self._summarize_messages(older)
            result = [system_msg] if system_msg else []
            if summary:
                result.append({"role": "user", "content": f"[对话摘要] {summary}"})
            result.extend(recent)
            return result

        # If even recent messages don't fit, trim them
        trimmed = []
        tokens_used = 0
        for msg in reversed(recent):
            msg_tokens = self.estimate_message_tokens(msg)
            if tokens_used + msg_tokens <= self.available_tokens:
                trimmed.insert(0, msg)
                tokens_used += msg_tokens
            else:
                break

        result = [system_msg] if system_msg else []
        result.extend(trimmed)
        return result

    def _summarize_messages(self, messages: list[dict[str, Any]]) -> str:
        """Create a brief summary of messages."""
        if not messages:
            return ""

        summaries = []
        for msg in messages:
            role = msg.get("role", "")
            content = msg.get("content", "")

            if role == "user":
                summaries.append(f"用户: {self._truncate(content, 50)}")
            elif role == "assistant":
                if msg.get("tool_calls"):
                    tool_names = [tc["function"]["name"] for tc in msg["tool_calls"]]
                    summaries.append(f"助手调用工具: {', '.join(tool_names)}")
                elif content:
                    summaries.append(f"助手: {self._truncate(content, 50)}")
            elif role == "tool":
                summaries.append("工具返回结果")

        return " | ".join(summaries[:10])  # Max 10 items in summary

    def _truncate(self, text: str, max_len: int) -> str:
        """Truncate text to max length."""
        if isinstance(text, str) and len(text) > max_len:
            return text[:max_len] + "..."
        return str(text)

    def should_summarize(self, messages: list[dict[str, Any]]) -> bool:
        """Check if messages should be summarized."""
        total_tokens = sum(self.estimate_message_tokens(msg) for msg in messages)
        return total_tokens > self.available_tokens * 0.8  # 80% threshold
