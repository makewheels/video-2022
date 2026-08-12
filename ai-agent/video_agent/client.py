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

    def __init__(self, model: str | None = None, base_url: str | None = None, api_key: str | None = None) -> None:
        cfg = get_config()
        self.base_url = base_url or cfg.base_url
        self.api_key = api_key or cfg.api_key
        self.model = model or cfg.model
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
            "description": "列出我的视频（按上传时间倒序，keyword 匹配标题/描述/标签）。用于'我上传了几个视频''最早/最近上传''我的视频列表'。返回 {list, total}，list 项含 id/title/status/watchCount/createTime。若最终目的是拿某个具体视频的 video_id，优先用 resolve_videos（返回更精简）。",
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
            "name": "resolve_videos",
            "description": "按标题关键词从我的视频中解析出候选及 video_id。凡用户用标题（如《xx》）指代视频、而后续操作需要 video_id 时，先调它。返回 {candidates: [{videoId, title, status, watchCount, createTime}], total}：0 个候选=没有匹配，如实告知用户；1 个=直接用其 video_id；多个=列出让用户选，不要自己猜。示例：resolve_videos(keyword='春节旅行')",
            "parameters": {
                "type": "object",
                "properties": {
                    "keyword": {"type": "string", "description": "视频标题关键词（可截取书名号内文字）"},
                    "limit": {"type": "integer", "default": 5, "description": "最多返回几个候选"},
                },
                "required": ["keyword"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_video_detail",
            "description": "获取单个视频详情：播放量、描述、标签、创建时间等。用于'《xx》播放量多少''视频详细信息'。需要 video_id——用户只给标题时先 resolve_videos 消歧。",
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
            "description": "获取视频处理状态。用于'转码好了没''处理到哪了'。返回 {status, isReady}，status 常见值 READY/TRANSCODING/FAILED。需要 video_id（用户只给标题时先 resolve_videos）。",
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
            "description": "获取单个视频的流量消耗（字节数）。用于'这个视频耗了多少流量'。需要 video_id（标题先 resolve_videos）；全账号按日流量趋势用 get_traffic_stats。",
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
            "description": "更新视频标题/描述/可见性。⚠️ 写操作需确认。需要 video_id（标题先 resolve_videos）。",
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
            "description": "删除视频，不可恢复。⚠️ 写操作需确认，确认时必须明确告知用户不可逆。需要 video_id（标题先 resolve_videos）。",
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
            "description": "上传本地视频文件（OSS 直传后自动触发转码，大文件耗时较长）。⚠️ 写操作需确认。file_path 必须是已存在的本地路径。示例：upload_video(file_path='./demo.mp4', title='演示', visibility='PRIVATE')",
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
            "description": "获取视频原始文件的下载链接。需要 video_id（标题先 resolve_videos）。",
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
            "description": "获取视频的评论数。用于'有几条评论'。返回 {count}。需要 video_id（标题先 resolve_videos）。",
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
            "description": "列出视频的评论（默认最新在前），返回 {list, total}。需要 video_id（标题先 resolve_videos）。看某条评论的回复用 comment_replies。",
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
            "description": "获取某条评论的回复列表。需要 comment_id（评论 ID，不是视频 ID）。",
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
            "description": "列出我的播放列表，返回 {list, total}。用于'我的播放列表有哪些'。看某个列表里有什么视频用 get_playlist_detail。",
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
            "description": "获取播放列表详情（含其中的视频列表）。用于'xx 播放列表里有什么'。需要 playlist_id。",
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
    {
        "type": "function",
        "function": {
            "name": "move_playlist_item",
            "description": "调整视频在播放列表中的位置（排序）。用于'把 xx 移到播放列表最前面/第 N 个'。⚠️ 写操作。to_index 为目标位置（0 开头）。需要 playlist_id 和 video_id（视频用标题指代时先 resolve_videos）。",
            "parameters": {
                "type": "object",
                "properties": {
                    "playlist_id": {"type": "string", "description": "播放列表 ID"},
                    "video_id": {"type": "string", "description": "要移动的视频 ID"},
                    "to_index": {"type": "integer", "description": "目标索引位置（0 开头）"},
                },
                "required": ["playlist_id", "video_id", "to_index"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "recover_playlist",
            "description": "恢复已删除的播放列表（删除是逻辑删除，可恢复）。用于'找回/恢复之前删掉的播放列表'。⚠️ 写操作。",
            "parameters": {
                "type": "object",
                "properties": {
                    "playlist_id": {"type": "string", "description": "要恢复的播放列表 ID"},
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
            "description": "获取未读通知数量，无参数。用于'我有几条未读通知'。返回 {count}。",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_notifications",
            "description": "列出我的通知（page/page_size 分页）。用于'最近有什么通知'。",
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
            "description": "查询当前用户对某个视频是否已点赞/点踩。用于'我点赞了吗''有没有点踩'。返回 {liked, disliked}。需要 video_id（标题先 resolve_videos）。",
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
            "description": "查询分享短链的统计数据（点击量等）。用于'分享链接被点了多少次'。需要 short_code（分享短码，如 abc123）。",
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
            "description": "我的观看历史（按观看时间倒序，含 videoId/title/watchTime）。用于'我最近看过什么'。注意：历史项不含播放进度，查进度用 get_watch_progress。",
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
            "description": "获取视频的播放进度。用于回答'上次看到哪里了'等。注意：进度按设备存储，必须提供 client_id（观看设备标识），否则会返回错误。",
            "parameters": {
                "type": "object",
                "properties": {
                    "video_id": {"type": "string", "description": "视频 ID"},
                    "client_id": {"type": "string", "description": "观看设备标识（必需）"},
                },
                "required": ["video_id", "client_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "clear_watch_history",
            "description": "清空全部观看历史，不可恢复。⚠️ 写操作需确认。",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    # ── Search ──
    {
        "type": "function",
        "function": {
            "name": "search_public_videos",
            "description": "搜索全站公开视频。用于'有没有 xx 视频''找 xx 类视频'。注意：只含公开视频，搜我自己的视频（含私密）用 list_my_videos。keyword、category（如'教育''美食'）均可选。示例：search_public_videos(keyword='AI', category='教育')",
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
    {
        "type": "function",
        "function": {
            "name": "get_public_video_list",
            "description": "浏览全站公开视频信息流（按时间倒序，无需登录，返回 {list, total}）。用于'首页有什么视频''最近大家发了什么'这类无明确目标的浏览。与 search_public_videos 的区别：它支持 skip/limit 分页但只按 keyword 过滤标题/描述，不支持分类筛选；要找某类视频优先 search_public_videos。",
            "parameters": {
                "type": "object",
                "properties": {
                    "skip": {"type": "integer", "default": 0, "description": "跳过的记录数"},
                    "limit": {"type": "integer", "default": 20, "description": "返回的记录数"},
                    "keyword": {"type": "string", "description": "可选关键词，过滤标题/描述"},
                },
            },
        },
    },
    # ── Stats ──
    {
        "type": "function",
        "function": {
            "name": "get_traffic_stats",
            "description": "全账号按日聚合的流量统计，days 默认 7 天。用于'最近流量趋势''每天消耗多少'。单个视频的流量用 get_video_traffic。",
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
            "description": "获取当前登录用户的信息（昵称、手机号等）。用于'我是谁''我的账号信息'。",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_my_profile",
            "description": "获取我的个人资料页信息：昵称、简介 bio、头像 avatarUrl、订阅数 subscriberCount、视频数 videoCount。用于'我的主页/资料长什么样''我有多少订阅者'。与 get_my_info 的区别：get_my_info 偏账号信息（手机号），get_my_profile 偏对外展示的资料。",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "update_profile",
            "description": "更新我的个人资料（昵称、简介 bio）。⚠️ 写操作需确认。nickname 和 bio 至少提供一个；昵称上限 30 字，简介上限 200 字。",
            "parameters": {
                "type": "object",
                "properties": {
                    "nickname": {"type": "string", "description": "新昵称（最长 30 字）"},
                    "bio": {"type": "string", "description": "新个人简介（最长 200 字）"},
                },
            },
        },
    },
    # ── Channel / Subscription ──
    {
        "type": "function",
        "function": {
            "name": "subscribe_channel",
            "description": "订阅某个用户的频道。⚠️ 写操作需确认。channel_user_id 是频道主的用户 ID（不是视频 ID）；不确定时先 get_channel 确认对方信息。",
            "parameters": {
                "type": "object",
                "properties": {
                    "channel_user_id": {"type": "string", "description": "频道主的用户 ID"},
                },
                "required": ["channel_user_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "unsubscribe_channel",
            "description": "取消订阅某个频道。⚠️ 写操作需确认。channel_user_id 是频道主的用户 ID。",
            "parameters": {
                "type": "object",
                "properties": {
                    "channel_user_id": {"type": "string", "description": "频道主的用户 ID"},
                },
                "required": ["channel_user_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_my_subscriptions",
            "description": "列出我订阅的频道（返回 {list, total}，list 是频道主的用户 ID 字符串）。用于'我订阅了哪些频道'。想看某个频道的昵称/头像等详情，拿返回的 userId 再调 get_channel。",
            "parameters": {
                "type": "object",
                "properties": {
                    "skip": {"type": "integer", "default": 0, "description": "跳过的记录数"},
                    "limit": {"type": "integer", "default": 20, "description": "返回的记录数"},
                },
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_channel",
            "description": "获取某个用户的频道公开信息：昵称、头像、简介、订阅数 subscriberCount、视频数 videoCount。用于'xx 的频道有多少订阅/视频'。需要 user_id（频道主的用户 ID）。",
            "parameters": {
                "type": "object",
                "properties": {
                    "user_id": {"type": "string", "description": "频道主的用户 ID"},
                },
                "required": ["user_id"],
            },
        },
    },
    # ── YouTube ──
    {
        "type": "function",
        "function": {
            "name": "get_youtube_info",
            "description": "获取 YouTube 视频信息。用于回答'这个 YouTube 视频怎么样'等。支持完整 URL（watch?v=、youtu.be、shorts 链接）或 11 位视频 ID。",
            "parameters": {
                "type": "object",
                "properties": {
                    "url": {"type": "string", "description": "YouTube 视频 URL 或 11 位视频 ID"},
                },
                "required": ["url"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "transfer_youtube",
            "description": "将 YouTube 视频转存到平台。⚠️ 写操作，需要确认。支持完整 URL 或 11 位视频 ID。",
            "parameters": {
                "type": "object",
                "properties": {
                    "url": {"type": "string", "description": "YouTube 视频 URL 或 11 位视频 ID"},
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
