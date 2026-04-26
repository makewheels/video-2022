"""Anthropic Agent SDK client with session management."""

from __future__ import annotations

import json
import os
from typing import Any
from collections import defaultdict

from anthropic import Anthropic
from anthropic.types import Message, MessageParam, ToolUseBlock, TextBlock


class SessionManager:
    """Manages conversation sessions with message history."""

    def __init__(self):
        self.sessions: dict[str, list[MessageParam]] = defaultdict(list)

    def get_messages(self, session_id: str) -> list[MessageParam]:
        """Get message history for a session."""
        return self.sessions[session_id]

    def add_message(self, session_id: str, message: MessageParam):
        """Add a message to session history."""
        self.sessions[session_id].append(message)

    def clear_session(self, session_id: str):
        """Clear a session's history."""
        if session_id in self.sessions:
            del self.sessions[session_id]


class AnthropicAgentClient:
    """Anthropic Agent SDK client with tool calling and session management."""

    def __init__(self, api_key: str | None = None, model: str = "claude-3-5-sonnet-20241022"):
        self.api_key = api_key or os.getenv("ANTHROPIC_API_KEY")
        if not self.api_key:
            raise ValueError("ANTHROPIC_API_KEY not set")

        self.client = Anthropic(api_key=self.api_key)
        self.model = model
        self.session_manager = SessionManager()

    def chat(
        self,
        query: str,
        session_id: str,
        system_prompt: str,
        tools: list[dict[str, Any]],
        max_tokens: int = 4096,
    ) -> tuple[Message, list[MessageParam]]:
        """
        Send a chat message and return the response.

        Returns:
            (response, updated_messages) - The API response and full message history
        """
        # Get existing messages for this session
        messages = self.session_manager.get_messages(session_id)

        # Add user message
        user_message: MessageParam = {"role": "user", "content": query}
        messages.append(user_message)

        # Call Claude
        response = self.client.messages.create(
            model=self.model,
            max_tokens=max_tokens,
            system=system_prompt,
            messages=messages,
            tools=tools,
        )

        # Add assistant response to history
        assistant_message: MessageParam = {
            "role": "assistant",
            "content": response.content,
        }
        messages.append(assistant_message)

        # Update session
        self.session_manager.sessions[session_id] = messages

        return response, messages

    def add_tool_result(
        self,
        session_id: str,
        tool_use_id: str,
        result: Any,
    ):
        """Add a tool result to the conversation."""
        messages = self.session_manager.get_messages(session_id)

        tool_result: MessageParam = {
            "role": "user",
            "content": [
                {
                    "type": "tool_result",
                    "tool_use_id": tool_use_id,
                    "content": json.dumps(result, ensure_ascii=False, default=str),
                }
            ],
        }
        messages.append(tool_result)
        self.session_manager.sessions[session_id] = messages

    def continue_conversation(
        self,
        session_id: str,
        system_prompt: str,
        tools: list[dict[str, Any]],
        max_tokens: int = 4096,
    ) -> Message:
        """Continue the conversation after tool results have been added."""
        messages = self.session_manager.get_messages(session_id)

        response = self.client.messages.create(
            model=self.model,
            max_tokens=max_tokens,
            system=system_prompt,
            messages=messages,
            tools=tools,
        )

        # Add assistant response to history
        assistant_message: MessageParam = {
            "role": "assistant",
            "content": response.content,
        }
        messages.append(assistant_message)
        self.session_manager.sessions[session_id] = messages

        return response

    def clear_session(self, session_id: str):
        """Clear a session's conversation history."""
        self.session_manager.clear_session(session_id)

    def get_text_from_response(self, response: Message) -> str:
        """Extract text content from response."""
        text_parts = []
        for block in response.content:
            if isinstance(block, TextBlock):
                text_parts.append(block.text)
        return "".join(text_parts)

    def get_tool_calls_from_response(self, response: Message) -> list[dict[str, Any]]:
        """Extract tool calls from response."""
        tool_calls = []
        for block in response.content:
            if isinstance(block, ToolUseBlock):
                tool_calls.append({
                    "id": block.id,
                    "name": block.name,
                    "input": block.input,
                })
        return tool_calls
