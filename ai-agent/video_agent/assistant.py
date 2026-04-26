"""Agent loop — model-driven tool-use with streaming and multi-turn sessions."""

from __future__ import annotations

import json
import re
import sys
from typing import Any

from .client import ALL_TOOLS, AgentResponse, ModelClient, ToolCall
from .tools import VideoTools


SYSTEM_PROMPT = """你是 video-2022 视频分享平台的 AI 助手。你帮助用户管理视频、查看数据、处理评论、播放列表、通知等。

## 核心规则

1. **写操作必须确认**：涉及上传、删除、创建、更新、点赞、点踩等修改数据的操作，必须先告知用户计划并请求确认，不要直接执行。工具会返回 requiresConfirmation，你应据此告知用户。
2. **不确定就追问**：当关键词匹配到多个视频时，列出候选让用户选择，不要猜。
3. **用最合适的工具**：每个意图有对应的工具，仔细选择。
4. **中文回答**：始终用中文回答。视频标题用《》包起来。
5. **数据准确**：回答中涉及的数字（播放量、评论数等）必须来自工具返回结果，不要编造。

## 工具使用指南

- 查视频数量、列表 → list_my_videos
- 查最早/最近上传 → list_my_videos（自己排序 createTime）
- 查播放量 → get_video_detail
- 查处理状态 → get_video_status
- 查流量消耗 → get_video_traffic
- 查观看历史 → watch_history
- 查评论数 → comment_count
- 查评论内容 → list_comments
- 查播放列表 → list_playlists
- 查未读通知 → unread_notification_count
- 查点赞状态 → like_status
- 搜索公开视频 → search_public_videos
- 个人信息 → get_my_info
- 创建分享链接 → create_share（写操作，需确认）
- 上传视频 → upload_video（写操作，需确认）
- 其他写操作同理，都需确认"""


class VideoAssistant:
    def __init__(
        self,
        tools: VideoTools,
        model: str | None = None,
        base_url: str | None = None,
        api_key: str | None = None,
    ) -> None:
        self.tools = tools
        self.client = ModelClient()
        if model:
            self.client.model = model
        if base_url:
            self.client.base_url = base_url
        if api_key:
            self.client.api_key = api_key

    def answer(self, query: str) -> dict[str, Any]:
        """Single-turn answer (non-streaming, for eval)."""
        self.tools.trace.clear()
        messages: list[dict[str, Any]] = [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": query},
        ]
        final_text, tool_trace = self._agent_loop(messages)
        return {
            "query": query,
            "answer": final_text,
            "trace": tool_trace,
        }

    def chat(self, query: str, history: list[dict[str, Any]] | None = None) -> dict[str, Any]:
        """Multi-turn chat with history."""
        self.tools.trace.clear()
        messages = [{"role": "system", "content": SYSTEM_PROMPT}]
        if history:
            messages.extend(history)
        messages.append({"role": "user", "content": query})
        final_text, tool_trace = self._agent_loop(messages)
        return {
            "query": query,
            "answer": final_text,
            "trace": tool_trace,
            "messages": messages,  # return full conversation for next turn
        }

    def chat_stream(self, query: str, history: list[dict[str, Any]] | None = None):
        """Multi-turn chat with streaming output to stdout."""
        self.tools.trace.clear()
        messages = [{"role": "system", "content": SYSTEM_PROMPT}]
        if history:
            messages.extend(history)
        messages.append({"role": "user", "content": query})

        max_turns = 8
        for turn in range(max_turns):
            # Stream the response
            text = ""
            tool_calls: list[ToolCall] = []

            for event in self.client.chat_stream(messages):
                if event["type"] == "text_delta":
                    sys.stdout.write(event["text"])
                    sys.stdout.flush()
                    text += event["text"]
                elif event["type"] == "tool_call_delta":
                    pass  # handled at done
                elif event["type"] == "done":
                    tool_calls = event.get("tool_calls", [])
                    if not text and not tool_calls:
                        text = event.get("text", "")

            if not tool_calls:
                sys.stdout.write("\n")
                sys.stdout.flush()
                return

            # Execute tools
            print()  # newline before tool calls
            messages.append({"role": "assistant", "content": text or None, "tool_calls": [
                {"id": tc.id, "type": "function", "function": {"name": tc.name, "arguments": json.dumps(tc.arguments, ensure_ascii=False)}}
                for tc in tool_calls
            ]})
            # Also add with null content if empty
            if not text:
                messages[-1]["content"] = None

            for tc in tool_calls:
                result = self.tools.execute(tc.name, tc.arguments)
                requires = result.get("requiresConfirmation") if isinstance(result, dict) else False
                if requires:
                    sys.stdout.write(f"  ⚠️  {result.get('message', '需要确认')}\n")
                    sys.stdout.write(f"     计划: {json.dumps(result.get('planned', {}), ensure_ascii=False)}\n")
                else:
                    summary = _summarize_result(result)
                    sys.stdout.write(f"  🔧 {tc.name}: {summary}\n")
                sys.stdout.flush()
                messages.append({
                    "role": "tool",
                    "tool_call_id": tc.id,
                    "content": json.dumps(result, ensure_ascii=False, default=str),
                })

            if any(isinstance(tc.result, dict) and tc.result.get("requiresConfirmation") for tc in self.tools.trace):
                print("\n⚠️ 以上写操作需要确认。使用 --confirm-write 来执行。")
                return

            sys.stdout.write("\n")
            sys.stdout.flush()

        print("\n(达到最大推理轮次)")

    # ── Internal ─────────────────────────────────────────────────

    def _agent_loop(self, messages: list[dict[str, Any]], max_turns: int = 8) -> tuple[str, list[dict[str, Any]]]:
        """Execute the agent loop: model → tools → model → ... until text response."""
        for _ in range(max_turns):
            response = self.client.chat(messages, stream=False)

            if not response.has_tool_calls:
                return response.text, [
                    {"name": tc.name, "args": tc.args, "result": tc.result}
                    for tc in self.tools.trace
                ]

            # Append assistant message
            assistant_msg: dict[str, Any] = {"role": "assistant", "content": response.text or None}
            if response.tool_calls:
                assistant_msg["tool_calls"] = [
                    {"id": tc.id, "type": "function", "function": {"name": tc.name, "arguments": json.dumps(tc.arguments, ensure_ascii=False)}}
                    for tc in response.tool_calls
                ]
            messages.append(assistant_msg)

            # Execute tools and append results
            for tc in response.tool_calls:
                result = self.tools.execute(tc.name, tc.arguments)
                messages.append({
                    "role": "tool",
                    "tool_call_id": tc.id,
                    "content": json.dumps(result, ensure_ascii=False, default=str),
                })

            # If any tool returned requiresConfirmation, stop and let assistant respond
            if any(isinstance(tc.result, dict) and tc.result.get("requiresConfirmation") for tc in self.tools.trace):
                # Let the model respond to the confirmation result
                final = self.client.chat(messages, stream=False)
                return final.text, [
                    {"name": tc.name, "args": tc.args, "result": tc.result}
                    for tc in self.tools.trace
                ]

        return "抱歉，推理轮次超限。请简化问题重试。", []


def _summarize_result(result: Any, max_len: int = 80) -> str:
    """Brief summary of a tool result for display."""
    if not isinstance(result, dict):
        return str(result)[:max_len]
    if "error" in result:
        return f"❌ {result['error']}"[:max_len]
    # try common fields
    for key in ("total", "count", "status", "videoId", "title"):
        if key in result:
            return f"{key}={result[key]}"
    return json.dumps(result, ensure_ascii=False)[:max_len]
