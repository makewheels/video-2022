"""Model client abstraction — OpenAI-compatible by default, Anthropic optional."""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from typing import Any, Iterator

from .config import get_config


@dataclass
class ToolCall:
    id: str
    name: str
    arguments: dict[str, Any]


@dataclass
class AgentResponse:
    """One step of the agent loop."""
    text: str = ""
    tool_calls: list[ToolCall] = field(default_factory=list)
    finish_reason: str = "stop"
    usage: dict[str, int] = field(default_factory=dict)

    @property
    def has_tool_calls(self) -> bool:
        return len(self.tool_calls) > 0


class ModelClient:
    """OpenAI-compatible chat completions client.

    Also supports Anthropic Messages API via the same interface.
    """

    def __init__(self) -> None:
        cfg = get_config()
        self.base_url = cfg.base_url
        self.api_key = cfg.api_key
        self.model = cfg.model
        self.temperature = cfg.temperature
        self.max_tokens = cfg.max_tokens
        self.timeout = cfg.timeout

    def chat(
        self,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]] | None = None,
        *,
        stream: bool = False,
    ) -> AgentResponse:
        """Send a chat request and return the response."""
        if tools is None:
            tools = ALL_TOOLS

        body = {
            "model": self.model,
            "messages": messages,
            "temperature": self.temperature,
            "max_tokens": self.max_tokens,
            "stream": stream,
        }
        if tools:
            body["tools"] = tools
            body["tool_choice"] = "auto"

        if stream:
            return self._handle_stream(body)
        return self._handle_nonstream(body)

    def _handle_nonstream(self, body: dict[str, Any]) -> AgentResponse:
        import urllib.request as req

        data = json.dumps(body).encode("utf-8")
        request = req.Request(
            f"{self.base_url}/chat/completions",
            data=data,
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {self.api_key}",
            },
            method="POST",
        )
        with req.urlopen(request, timeout=self.timeout) as resp:
            result = json.loads(resp.read().decode("utf-8"))

        choice = result["choices"][0]
        message = choice["message"]
        # Strip thinking content (MiniMax M2.7)
        content = message.get("content") or ""
        content = _strip_thinking(content)
        usage = result.get("usage", {})

        tool_calls = []
        for tc in message.get("tool_calls", []):
            fn = tc["function"]
            try:
                args = json.loads(fn["arguments"])
            except (json.JSONDecodeError, TypeError):
                args = {}
            tool_calls.append(ToolCall(id=tc.get("id", ""), name=fn["name"], arguments=args))

        return AgentResponse(
            text=content,
            tool_calls=tool_calls,
            finish_reason=choice.get("finish_reason", "stop"),
            usage=dict(usage),
        )

    def _handle_stream(self, body: dict[str, Any]) -> AgentResponse:
        """Streaming chat that yields deltas, returns the accumulated response."""
        # Non-streaming for simplicity in the first version;
        # streaming events are handled in the assistant layer.
        return self._handle_nonstream(body)

    def chat_stream(
        self,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]] | None = None,
    ) -> Iterator[dict[str, Any]]:
        """Yield SSE events as dicts: {"type": "text_delta", "text": "..."}
        or {"type": "tool_call_delta", ...} or {"type": "done", "usage": ...}."""
        if tools is None:
            tools = ALL_TOOLS

        import urllib.request as req

        body = json.dumps({
            "model": self.model,
            "messages": messages,
            "temperature": self.temperature,
            "max_tokens": self.max_tokens,
            "stream": True,
            "tools": tools,
            "tool_choice": "auto",
        }).encode("utf-8")

        request = req.Request(
            f"{self.base_url}/chat/completions",
            data=body,
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {self.api_key}",
            },
            method="POST",
        )

        with req.urlopen(request, timeout=self.timeout) as resp:
            content_parts: list[str] = []
            tool_call_buf: dict[int, dict[str, Any]] = {}
            finish_reason = ""
            usage = {}
            for line_bytes in resp:
                line = line_bytes.decode("utf-8").strip()
                if not line.startswith("data: "):
                    continue
                payload = line[6:]
                if payload == "[DONE]":
                    break
                try:
                    event = json.loads(payload)
                except json.JSONDecodeError:
                    continue

                choice = event.get("choices", [{}])[0]
                delta = choice.get("delta", {})
                finish_reason = finish_reason or choice.get("finish_reason", "")
                event_usage = event.get("usage")
                if event_usage:
                    usage = event_usage

                # Skip thinking/reasoning content (MiniMax M2.7)
                text_delta = delta.get("content", "")
                if text_delta and not delta.get("reasoning_content"):
                    text_delta = _strip_thinking(text_delta)
                    if text_delta:
                        content_parts.append(text_delta)
                        yield {"type": "text_delta", "text": text_delta}

                for tc in delta.get("tool_calls", []):
                    idx = tc.get("index", 0)
                    if idx not in tool_call_buf:
                        tool_call_buf[idx] = {"id": tc.get("id", ""), "name": "", "arguments": ""}
                    if "id" in tc:
                        tool_call_buf[idx]["id"] = tc["id"]
                    if "function" in tc:
                        if "name" in tc["function"] and tc["function"]["name"]:
                            tool_call_buf[idx]["name"] += tc["function"]["name"]
                        if "arguments" in tc["function"]:
                            tool_call_buf[idx]["arguments"] += tc["function"]["arguments"]
                            yield {"type": "tool_call_delta", "index": idx, "name": tool_call_buf[idx]["name"], "arguments": tool_call_buf[idx]["arguments"]}

            # Build final tool_calls
            tool_calls = []
            for idx in sorted(tool_call_buf):
                buf = tool_call_buf[idx]
                try:
                    args = json.loads(buf["arguments"])
                except (json.JSONDecodeError, TypeError):
                    args = {}
                tool_calls.append(ToolCall(id=buf["id"], name=buf["name"], arguments=args))

            yield {
                "type": "done",
                "text": "".join(content_parts),
                "tool_calls": tool_calls,
                "finish_reason": finish_reason,
                "usage": dict(usage) if usage else {},
            }


# ── Tool definitions (OpenAI function-calling format) ────────────

ALL_TOOLS: list[dict[str, Any]] = [
    # ── Video ──
    {
        "type": "function",
        "function": {
            "name": "list_my_videos",
            "description": "列出我的视频列表，支持分页和关键词搜索。用于回答'我上传了几个视频'、'我有哪些视频'、'找某个标题的视频'等。",
            "parameters": {
                "type": "object",
                "properties": {
                    "keyword": {"type": "string", "description": "搜索关键词，匹配标题、描述、标签"},
                    "skip": {"type": "integer", "default": 0, "description": "跳过的记录数"},
                    "limit": {"type": "integer", "default": 20, "description": "返回的记录数"},
                },
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_video_detail",
            "description": "获取视频详细信息，包括播放量、描述、标签、创建时间等。用于回答'某个视频的播放量'、'视频的详细信息'等。",
            "parameters": {
                "type": "object",
                "properties": {
                    "video_id": {"type": "string", "description": "视频 ID"},
                },
                "required": ["video_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_video_status",
            "description": "获取视频处理状态（如 READY, TRANSCODING, FAILED 等）。用于回答'转码好了没'、'视频处理状态'等。",
            "parameters": {
                "type": "object",
                "properties": {
                    "video_id": {"type": "string", "description": "视频 ID"},
                },
                "required": ["video_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_video_traffic",
            "description": "获取视频的流量消耗（字节数）。用于回答'视频消耗了多少流量'、'流量使用情况'等。",
            "parameters": {
                "type": "object",
                "properties": {
                    "video_id": {"type": "string", "description": "视频 ID"},
                },
                "required": ["video_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "update_video",
            "description": "更新视频元数据（标题、描述、可见性）。⚠️ 写操作，执行前必须确认。",
            "parameters": {
                "type": "object",
                "properties": {
                    "video_id": {"type": "string", "description": "要更新的视频 ID"},
                    "title": {"type": "string", "description": "新标题"},
                    "description": {"type": "string", "description": "新描述"},
                    "visibility": {"type": "string", "enum": ["PUBLIC", "PRIVATE", "UNLISTED"], "description": "可见性：PUBLIC 公开，PRIVATE 私密，UNLISTED 不列出"},
                },
                "required": ["video_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "delete_video",
            "description": "删除视频。⚠️ 写操作，不可逆，执行前必须确认。",
            "parameters": {
                "type": "object",
                "properties": {
                    "video_id": {"type": "string", "description": "要删除的视频 ID"},
                },
                "required": ["video_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "upload_video",
            "description": "上传视频文件。⚠️ 写操作，需要 --confirm-write。",
            "parameters": {
                "type": "object",
                "properties": {
                    "file_path": {"type": "string", "description": "本地视频文件路径"},
                    "title": {"type": "string", "description": "视频标题（可选，默认用文件名）"},
                    "visibility": {"type": "string", "enum": ["PUBLIC", "PRIVATE", "UNLISTED"], "description": "可见性设置"},
                },
                "required": ["file_path"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_video_download_url",
            "description": "获取视频原始文件的下载链接。",
            "parameters": {
                "type": "object",
                "properties": {
                    "video_id": {"type": "string", "description": "视频 ID"},
                },
                "required": ["video_id"],
            },
        },
    },
    # ── Comment ──
    {
        "type": "function",
        "function": {
            "name": "comment_count",
            "description": "获取视频的评论数。用于回答'有几条评论'、'评论数多少'等。",
            "parameters": {
                "type": "object",
                "properties": {
                    "video_id": {"type": "string", "description": "视频 ID"},
                },
                "required": ["video_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_comments",
            "description": "列出视频的评论列表。用于回答'评论有哪些'、'看看评论'等。",
            "parameters": {
                "type": "object",
                "properties": {
                    "video_id": {"type": "string", "description": "视频 ID"},
                    "skip": {"type": "integer", "default": 0, "description": "跳过的记录数"},
                    "limit": {"type": "integer", "default": 20, "description": "返回的记录数"},
                },
                "required": ["video_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "add_comment",
            "description": "给视频添加评论。⚠️ 写操作，执行前必须确认。",
            "parameters": {
                "type": "object",
                "properties": {
                    "video_id": {"type": "string", "description": "视频 ID"},
                    "content": {"type": "string", "description": "评论内容"},
                    "parent_comment_id": {"type": "string", "description": "父评论 ID（回复时使用）"},
                },
                "required": ["video_id", "content"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "delete_comment",
            "description": "删除评论。⚠️ 写操作，不可逆，执行前必须确认。",
            "parameters": {
                "type": "object",
                "properties": {
                    "comment_id": {"type": "string", "description": "评论 ID"},
                },
                "required": ["comment_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "like_comment",
            "description": "点赞一条评论。⚠️ 写操作。",
            "parameters": {
                "type": "object",
                "properties": {
                    "comment_id": {"type": "string", "description": "评论 ID"},
                },
                "required": ["comment_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "comment_replies",
            "description": "获取某条评论的回复列表。",
            "parameters": {
                "type": "object",
                "properties": {
                    "comment_id": {"type": "string", "description": "父评论 ID"},
                },
                "required": ["comment_id"],
            },
        },
    },
    # ── Playlist ──
    {
        "type": "function",
        "function": {
            "name": "list_playlists",
            "description": "列出我的播放列表。用于回答'我的播放列表有哪些'等。",
            "parameters": {
                "type": "object",
                "properties": {
                    "skip": {"type": "integer", "default": 0},
                    "limit": {"type": "integer", "default": 20},
                },
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_playlist_detail",
            "description": "获取播放列表详细信息，包含视频列表。",
            "parameters": {
                "type": "object",
                "properties": {
                    "playlist_id": {"type": "string", "description": "播放列表 ID"},
                },
                "required": ["playlist_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "create_playlist",
            "description": "创建新的播放列表。⚠️ 写操作，执行前必须确认。",
            "parameters": {
                "type": "object",
                "properties": {
                    "title": {"type": "string", "description": "播放列表标题"},
                    "description": {"type": "string", "description": "播放列表描述"},
                },
                "required": ["title"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "add_video_to_playlist",
            "description": "向播放列表添加视频。⚠️ 写操作。",
            "parameters": {
                "type": "object",
                "properties": {
                    "playlist_id": {"type": "string", "description": "播放列表 ID"},
                    "video_id": {"type": "string", "description": "视频 ID"},
                },
                "required": ["playlist_id", "video_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "remove_video_from_playlist",
            "description": "从播放列表移除视频。⚠️ 写操作。",
            "parameters": {
                "type": "object",
                "properties": {
                    "playlist_id": {"type": "string", "description": "播放列表 ID"},
                    "video_id": {"type": "string", "description": "视频 ID"},
                },
                "required": ["playlist_id", "video_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "delete_playlist",
            "description": "删除播放列表。⚠️ 写操作，不可逆。",
            "parameters": {
                "type": "object",
                "properties": {
                    "playlist_id": {"type": "string", "description": "播放列表 ID"},
                },
                "required": ["playlist_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "update_playlist",
            "description": "更新播放列表信息。⚠️ 写操作。",
            "parameters": {
                "type": "object",
                "properties": {
                    "playlist_id": {"type": "string", "description": "播放列表 ID"},
                    "title": {"type": "string", "description": "新标题"},
                    "description": {"type": "string", "description": "新描述"},
                },
                "required": ["playlist_id"],
            },
        },
    },
    # ── Notification ──
    {
        "type": "function",
        "function": {
            "name": "unread_notification_count",
            "description": "获取未读通知数量。用于回答'我有几条未读通知'等。",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_notifications",
            "description": "列出我的通知列表。用于回答'最近通知有什么'等。",
            "parameters": {
                "type": "object",
                "properties": {
                    "page": {"type": "integer", "default": 0, "description": "页码（0-indexed）"},
                    "page_size": {"type": "integer", "default": 20, "description": "每页数量"},
                },
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "mark_notification_read",
            "description": "标记某条通知为已读。⚠️ 写操作。",
            "parameters": {
                "type": "object",
                "properties": {
                    "notification_id": {"type": "string", "description": "通知 ID"},
                },
                "required": ["notification_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "mark_all_notifications_read",
            "description": "标记所有通知为已读。⚠️ 写操作。",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    # ── Like / Dislike ──
    {
        "type": "function",
        "function": {
            "name": "like_status",
            "description": "查询当前用户对某个视频的点赞/点踩状态。用于回答'我点赞了吗'、'有没有点赞某个视频'等。",
            "parameters": {
                "type": "object",
                "properties": {
                    "video_id": {"type": "string", "description": "视频 ID"},
                },
                "required": ["video_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "like_video",
            "description": "点赞一个视频。⚠️ 写操作，执行前必须确认。",
            "parameters": {
                "type": "object",
                "properties": {
                    "video_id": {"type": "string", "description": "视频 ID"},
                },
                "required": ["video_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "dislike_video",
            "description": "点踩一个视频。⚠️ 写操作，执行前必须确认。",
            "parameters": {
                "type": "object",
                "properties": {
                    "video_id": {"type": "string", "description": "视频 ID"},
                },
                "required": ["video_id"],
            },
        },
    },
    # ── Share ──
    {
        "type": "function",
        "function": {
            "name": "create_share",
            "description": "创建视频分享链接。⚠️ 写操作，执行前必须确认。",
            "parameters": {
                "type": "object",
                "properties": {
                    "video_id": {"type": "string", "description": "视频 ID"},
                },
                "required": ["video_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "share_stats",
            "description": "获取分享链接的统计数据（点击量等）。用于回答'分享链接点击了多少次'等。",
            "parameters": {
                "type": "object",
                "properties": {
                    "short_code": {"type": "string", "description": "分享短码"},
                },
                "required": ["short_code"],
            },
        },
    },
    # ── Watch ──
    {
        "type": "function",
        "function": {
            "name": "watch_history",
            "description": "获取观看历史记录。用于回答'我最近看过什么'、'观看历史'等。",
            "parameters": {
                "type": "object",
                "properties": {
                    "page": {"type": "integer", "default": 0, "description": "页码"},
                    "page_size": {"type": "integer", "default": 20, "description": "每页数量"},
                },
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_watch_progress",
            "description": "获取视频的播放进度。用于回答'上次看到哪里了'等。",
            "parameters": {
                "type": "object",
                "properties": {
                    "video_id": {"type": "string", "description": "视频 ID"},
                },
                "required": ["video_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "clear_watch_history",
            "description": "清除所有观看历史。⚠️ 写操作，不可逆，执行前必须确认。",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    # ── Search ──
    {
        "type": "function",
        "function": {
            "name": "search_public_videos",
            "description": "搜索平台上的公开视频。用于回答'有没有XX视频'、'找XX类的视频'、'搜索XX'等。",
            "parameters": {
                "type": "object",
                "properties": {
                    "keyword": {"type": "string", "description": "搜索关键词"},
                    "category": {"type": "string", "description": "分类筛选，如 教育、美食、旅行 等"},
                    "page": {"type": "integer", "default": 0, "description": "页码"},
                    "page_size": {"type": "integer", "default": 20, "description": "每页数量"},
                },
            },
        },
    },
    # ── Stats ──
    {
        "type": "function",
        "function": {
            "name": "get_traffic_stats",
            "description": "获取按日聚合的流量统计数据。用于回答'流量趋势'、'每天消耗'等。",
            "parameters": {
                "type": "object",
                "properties": {
                    "days": {"type": "integer", "default": 7, "description": "统计天数"},
                },
            },
        },
    },
    # ── Auth / User ──
    {
        "type": "function",
        "function": {
            "name": "get_my_info",
            "description": "获取当前登录用户的信息。用于回答'我是谁'、'我的用户名'等。",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    # ── YouTube ──
    {
        "type": "function",
        "function": {
            "name": "get_youtube_info",
            "description": "获取 YouTube 视频信息。用于回答'这个 YouTube 视频怎么样'等。",
            "parameters": {
                "type": "object",
                "properties": {
                    "url": {"type": "string", "description": "YouTube 视频 URL"},
                },
                "required": ["url"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "transfer_youtube",
            "description": "将 YouTube 视频转存到平台。⚠️ 写操作，需要确认。",
            "parameters": {
                "type": "object",
                "properties": {
                    "url": {"type": "string", "description": "YouTube 视频 URL"},
                },
                "required": ["url"],
            },
        },
    },
]


def _strip_thinking(text: str) -> str:
    """Remove  thinking blocks from MiniMax M2.7 output."""
    import re
    return re.sub(r"<think>.*?</think>\s*", "", text, flags=re.DOTALL).strip()
