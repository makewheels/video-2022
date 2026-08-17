"""Tool execution layer — maps tool names to implementations.

Supports two backends:
- fixture:  offline test data from fixtures/videos.json
- cli:      real backend via video-cli
"""

from __future__ import annotations

import copy
import json
import os
import re
import subprocess
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import requests

from . import trace as lf_trace
from .config import get_config


@dataclass
class ToolCall:
    name: str
    args: dict[str, Any]
    result: Any = None


@dataclass
class VideoTools:
    backend: str = "fixture"
    fixture_path: str | None = None
    base_url: str | None = None
    token: str | None = None
    confirm_write: bool = False
    trace: list[ToolCall] = field(default_factory=list)
    _fixture_state: dict[str, Any] | None = field(default=None, init=False, repr=False)

    # ── Dispatch ─────────────────────────────────────────────────

    def execute(self, name: str, args: dict[str, Any]) -> Any:
        """Execute a tool by name and return its result."""
        method = getattr(self, name, None)
        if method is None:
            return {"error": f"Unknown tool: {name}"}
        span = lf_trace.start_tool_span(name, args)
        try:
            result = method(**args)
            self._record(name, args, result)
            lf_trace.finish_tool_span(span, result=result)
            return result
        except Exception as exc:
            error_result = {"error": str(exc), "tool": name}
            self._record(name, args, error_result)
            lf_trace.finish_tool_span(span, result=error_result, error=exc)
            return error_result

    # ── Video ────────────────────────────────────────────────────

    def list_my_videos(self, keyword: str | None = None, skip: int = 0, limit: int = 20) -> dict[str, Any]:
        if self.backend == "fixture":
            return self._fixture_list_videos(keyword, skip, limit)
        cmd = ["video", "list", "--skip", str(skip), "--limit", str(limit)]
        if keyword:
            cmd += ["--keyword", keyword]
        return self._run_cli(cmd)

    def get_video_detail(self, video_id: str) -> dict[str, Any]:
        if self.backend == "fixture":
            return self._fixture_get_video(video_id)
        return self._run_cli(["video", "detail", "--id", video_id])

    def get_video_status(self, video_id: str) -> dict[str, Any]:
        if self.backend == "fixture":
            v = self._fixture_get_video(video_id)
            return {"videoId": video_id, "status": v.get("status"), "isReady": v.get("status") == "READY"}
        return self._run_cli(["video", "status", "--id", video_id])

    def get_video_traffic(self, video_id: str) -> dict[str, Any]:
        if self.backend == "fixture":
            v = self._fixture_get_video(video_id)
            return {"videoId": video_id, "trafficBytes": v.get("trafficBytes", 0)}
        return self._run_cli(["stats", "traffic", "--video-id", video_id])

    def update_video(
        self,
        video_id: str,
        title: str | None = None,
        description: str | None = None,
        visibility: str | None = None,
    ) -> dict[str, Any]:
        if not self.confirm_write:
            return {
                "requiresConfirmation": True,
                "message": "update_video is write; rerun with --confirm-write",
                "planned": {"video_id": video_id, "title": title, "visibility": visibility},
            }
        if self.backend == "fixture":
            video = self._fixture_get_video(video_id)
            for key, value in (("title", title), ("description", description), ("visibility", visibility)):
                if value is not None:
                    video[key] = value
            return {"videoId": video_id, "updated": True}
        self._run_cli(
            [
                "video",
                "update",
                "--id",
                video_id,
                "--title",
                title or "",
                "--description",
                description or "",
                "--visibility",
                visibility or "",
            ]
        )
        return {"videoId": video_id, "updated": True}

    def delete_video(self, video_id: str) -> dict[str, Any]:
        if not self.confirm_write:
            return {
                "requiresConfirmation": True,
                "message": "delete_video is write, IRREVERSIBLE; rerun with --confirm-write",
                "planned": {"video_id": video_id},
            }
        if self.backend == "fixture":
            data = self._load_fixture()
            self._fixture_get_video(video_id)
            data["videos"] = [video for video in data.get("videos", []) if video.get("id") != video_id]
            return {"videoId": video_id, "deleted": True}
        self._run_cli(["video", "delete", "--id", video_id])
        return {"videoId": video_id, "deleted": True}

    def upload_video(self, file_path: str, title: str | None = None, visibility: str | None = None) -> dict[str, Any]:
        if not self.confirm_write:
            return {
                "requiresConfirmation": True,
                "message": "upload_video is write; rerun with --confirm-write",
                "planned": {"file_path": file_path, "title": title, "visibility": visibility},
            }
        if self.backend == "fixture":
            video_id = f"fixture-upload-{len(self._load_fixture().get('videos', [])) + 1}"
            self._load_fixture().setdefault("videos", []).append(
                {
                    "id": video_id,
                    "title": title or Path(file_path).stem,
                    "description": "",
                    "tags": [],
                    "category": "未分类",
                    "status": "CREATED",
                    "visibility": visibility or "PRIVATE",
                    "watchCount": 0,
                    "trafficBytes": 0,
                    "createTime": "fixture-now",
                }
            )
            return {"videoId": video_id, "status": "CREATED"}
        return self._upload_via_http(file_path, title=title, visibility=visibility)

    def get_video_download_url(self, video_id: str) -> dict[str, Any]:
        if self.backend == "fixture":
            return {"videoId": video_id, "downloadUrl": f"https://oneclick.video/file/access?videoId={video_id}"}
        return self._run_cli(["video", "download-url", "--id", video_id])

    # ── Comment ──────────────────────────────────────────────────

    def comment_count(self, video_id: str) -> dict[str, Any]:
        if self.backend == "fixture":
            count = len(self._load_fixture().get("comments", {}).get(video_id, []))
            return {"videoId": video_id, "count": count}
        return self._run_cli(["comment", "count", "--video-id", video_id])

    def list_comments(self, video_id: str, skip: int = 0, limit: int = 20) -> dict[str, Any]:
        if self.backend == "fixture":
            comments = self._load_fixture().get("comments", {}).get(video_id, [])
            return {"list": comments[skip: skip + limit], "total": len(comments)}
        return self._run_cli(["comment", "list", "--video-id", video_id, "--skip", str(skip), "--limit", str(limit)])

    def add_comment(self, video_id: str, content: str, parent_comment_id: str | None = None) -> dict[str, Any]:
        if not self.confirm_write:
            return {
                "requiresConfirmation": True,
                "message": "add_comment is write; rerun with --confirm-write",
                "planned": {"video_id": video_id, "content": content[:50]},
            }
        if self.backend == "fixture":
            self._fixture_get_video(video_id)
            comments = self._load_fixture().setdefault("comments", {}).setdefault(video_id, [])
            existing = sum(len(items) for items in self._load_fixture().get("comments", {}).values())
            comment_id = f"fixture-comment-{existing + 1}"
            comments.append(
                {"id": comment_id, "content": content, "likeCount": 0, "parentCommentId": parent_comment_id}
            )
            return {"commentId": comment_id, "videoId": video_id, "added": True}
        cmd = ["comment", "add", "--video-id", video_id, "--content", content]
        if parent_comment_id:
            cmd += ["--parent-id", parent_comment_id]
        return self._run_cli(cmd)

    def delete_comment(self, comment_id: str) -> dict[str, Any]:
        if not self.confirm_write:
            return {
                "requiresConfirmation": True,
                "message": "delete_comment is write, IRREVERSIBLE; rerun with --confirm-write",
                "planned": {"comment_id": comment_id},
            }
        if self.backend == "fixture":
            comments_by_video = self._load_fixture().setdefault("comments", {})
            for comments in comments_by_video.values():
                for index, comment in enumerate(comments):
                    if comment.get("id") == comment_id:
                        comments.pop(index)
                        return {"commentId": comment_id, "deleted": True}
            raise KeyError(f"comment not found: {comment_id}")
        self._run_cli(["comment", "delete", "--id", comment_id])
        return {"commentId": comment_id, "deleted": True}

    def like_comment(self, comment_id: str) -> dict[str, Any]:
        if not self.confirm_write:
            return {
                "requiresConfirmation": True,
                "message": "like_comment is write; rerun with --confirm-write",
                "planned": {"comment_id": comment_id},
            }
        if self.backend == "fixture":
            comment = self._fixture_comment(comment_id)
            comment["likeCount"] = int(comment.get("likeCount", 0)) + 1
            comment["liked"] = True
            return {"commentId": comment_id, "liked": True}
        return self._run_cli(["comment", "like", "--id", comment_id])

    def comment_replies(self, comment_id: str) -> dict[str, Any]:
        if self.backend == "fixture":
            return {"list": [], "total": 0, "parentCommentId": comment_id}
        return self._run_cli(["comment", "replies", "--parent-id", comment_id])

    # ── Playlist ─────────────────────────────────────────────────

    def list_playlists(self, skip: int = 0, limit: int = 20) -> dict[str, Any]:
        if self.backend == "fixture":
            pls = self._load_fixture().get("playlists", [])
            return {"list": pls[skip: skip + limit], "total": len(pls)}
        return self._run_cli(["playlist", "list", "--skip", str(skip), "--limit", str(limit)])

    def get_playlist_detail(self, playlist_id: str) -> dict[str, Any]:
        if self.backend == "fixture":
            for p in self._load_fixture().get("playlists", []):
                if p.get("id") == playlist_id:
                    return p
            return {"error": f"playlist not found: {playlist_id}"}
        return self._run_cli(["playlist", "detail", "--id", playlist_id])

    def create_playlist(self, title: str, description: str | None = None) -> dict[str, Any]:
        if not self.confirm_write:
            return {
                "requiresConfirmation": True,
                "message": "create_playlist is write; rerun with --confirm-write",
                "planned": {"title": title, "description": description},
            }
        if self.backend == "fixture":
            playlists = self._load_fixture().setdefault("playlists", [])
            playlist_id = f"fixture-playlist-{len(playlists) + 1}"
            playlists.append(
                {"id": playlist_id, "title": title, "description": description or "", "videoCount": 0, "videoIds": []}
            )
            return {"playlistId": playlist_id, "title": title, "created": True}
        cmd = ["playlist", "create", "--title", title]
        if description:
            cmd += ["--description", description]
        return self._run_cli(cmd)

    def add_video_to_playlist(self, playlist_id: str, video_id: str) -> dict[str, Any]:
        if not self.confirm_write:
            return {
                "requiresConfirmation": True,
                "message": "add_video_to_playlist is write; rerun with --confirm-write",
            }
        if self.backend == "fixture":
            playlist = self._fixture_playlist(playlist_id)
            self._fixture_get_video(video_id)
            ids = playlist.setdefault("videoIds", [])
            if video_id not in ids:
                ids.append(video_id)
            playlist["videoCount"] = len(ids)
            return {"playlistId": playlist_id, "videoId": video_id, "added": True}
        return self._run_cli(["playlist", "add-item", "--playlist-id", playlist_id, "--video-id", video_id])

    def remove_video_from_playlist(self, playlist_id: str, video_id: str) -> dict[str, Any]:
        if not self.confirm_write:
            return {
                "requiresConfirmation": True,
                "message": "remove_video_from_playlist is write; rerun with --confirm-write",
            }
        if self.backend == "fixture":
            playlist = self._fixture_playlist(playlist_id)
            ids = playlist.setdefault("videoIds", [])
            if video_id in ids:
                ids.remove(video_id)
            playlist["videoCount"] = len(ids)
            return {"playlistId": playlist_id, "videoId": video_id, "removed": True}
        return self._run_cli(["playlist", "delete-item", "--playlist-id", playlist_id, "--video-id", video_id])

    def delete_playlist(self, playlist_id: str) -> dict[str, Any]:
        if not self.confirm_write:
            return {
                "requiresConfirmation": True,
                "message": "delete_playlist is write, IRREVERSIBLE; rerun with --confirm-write",
                "planned": {"playlist_id": playlist_id},
            }
        if self.backend == "fixture":
            playlist = self._fixture_playlist(playlist_id)
            playlist["deleted"] = True
            return {"playlistId": playlist_id, "deleted": True}
        self._run_cli(["playlist", "delete", "--id", playlist_id])
        return {"playlistId": playlist_id, "deleted": True}

    def update_playlist(
        self, playlist_id: str, title: str | None = None, description: str | None = None
    ) -> dict[str, Any]:
        if not self.confirm_write:
            return {
                "requiresConfirmation": True,
                "message": "update_playlist is write; rerun with --confirm-write",
            }
        if self.backend == "fixture":
            playlist = self._fixture_playlist(playlist_id)
            if title is not None:
                playlist["title"] = title
            if description is not None:
                playlist["description"] = description
            return {"playlistId": playlist_id, "updated": True}
        cmd = ["playlist", "update", "--id", playlist_id]
        if title:
            cmd += ["--title", title]
        if description:
            cmd += ["--description", description]
        return self._run_cli(cmd)

    def move_playlist_item(self, playlist_id: str, video_id: str, to_index: int) -> dict[str, Any]:
        if not self.confirm_write:
            return {
                "requiresConfirmation": True,
                "message": "move_playlist_item is write; rerun with --confirm-write",
                "planned": {"playlist_id": playlist_id, "video_id": video_id, "to_index": to_index},
            }
        if self.backend == "fixture":
            playlist = self._fixture_playlist(playlist_id)
            ids = playlist.setdefault("videoIds", [])
            if video_id not in ids:
                raise KeyError(f"playlist item not found: {video_id}")
            ids.remove(video_id)
            ids.insert(max(0, min(to_index, len(ids))), video_id)
            return {"playlistId": playlist_id, "videoId": video_id, "toIndex": to_index, "moved": True}
        return self._run_cli(
            ["playlist", "move-item", "--playlist-id", playlist_id, "--video-id", video_id, "--to-index", str(to_index)]
        )

    def recover_playlist(self, playlist_id: str) -> dict[str, Any]:
        if not self.confirm_write:
            return {
                "requiresConfirmation": True,
                "message": "recover_playlist is write; rerun with --confirm-write",
                "planned": {"playlist_id": playlist_id},
            }
        if self.backend == "fixture":
            self._fixture_playlist(playlist_id)["deleted"] = False
            return {"playlistId": playlist_id, "recovered": True}
        return self._run_cli(["playlist", "recover", "--id", playlist_id])

    # ── Notification ─────────────────────────────────────────────

    def unread_notification_count(self) -> dict[str, Any]:
        if self.backend == "fixture":
            notifs = self._load_fixture().get("notifications", [])
            return {"count": len([n for n in notifs if not n.get("read")])}
        return self._run_cli(["notification", "unread-count"])

    def list_notifications(self, page: int = 0, page_size: int = 20) -> dict[str, Any]:
        if self.backend == "fixture":
            notifs = self._load_fixture().get("notifications", [])
            return {"list": notifs[page * page_size: (page + 1) * page_size], "total": len(notifs)}
        return self._run_cli(["notification", "list", "--page", str(page), "--page-size", str(page_size)])

    def mark_notification_read(self, notification_id: str) -> dict[str, Any]:
        if not self.confirm_write:
            return {
                "requiresConfirmation": True,
                "message": "mark_notification_read is write; rerun with --confirm-write",
            }
        if self.backend == "fixture":
            for notification in self._load_fixture().get("notifications", []):
                if notification.get("id") == notification_id:
                    notification["read"] = True
                    return {"notificationId": notification_id, "read": True}
            raise KeyError(f"notification not found: {notification_id}")
        return self._run_cli(["notification", "read", notification_id])

    def mark_all_notifications_read(self) -> dict[str, Any]:
        if not self.confirm_write:
            return {
                "requiresConfirmation": True,
                "message": "mark_all_notifications_read is write; rerun with --confirm-write",
            }
        if self.backend == "fixture":
            for notification in self._load_fixture().get("notifications", []):
                notification["read"] = True
            return {"allRead": True}
        return self._run_cli(["notification", "read-all"])

    # ── Like / Dislike ───────────────────────────────────────────

    def like_status(self, video_id: str) -> dict[str, Any]:
        if self.backend == "fixture":
            return self._load_fixture().get("likeStatus", {}).get(video_id, {"liked": False, "disliked": False})
        return self._run_cli(["like", "status", "--video-id", video_id])

    def like_video(self, video_id: str) -> dict[str, Any]:
        if not self.confirm_write:
            return {"requiresConfirmation": True, "message": "like_video is write; rerun with --confirm-write"}
        if self.backend == "fixture":
            self._fixture_get_video(video_id)
            self._load_fixture().setdefault("likeStatus", {})[video_id] = {"liked": True, "disliked": False}
            return {"videoId": video_id, "liked": True}
        return self._run_cli(["like", "like", "--video-id", video_id])

    def dislike_video(self, video_id: str) -> dict[str, Any]:
        if not self.confirm_write:
            return {"requiresConfirmation": True, "message": "dislike_video is write; rerun with --confirm-write"}
        if self.backend == "fixture":
            self._fixture_get_video(video_id)
            self._load_fixture().setdefault("likeStatus", {})[video_id] = {"liked": False, "disliked": True}
            return {"videoId": video_id, "disliked": True}
        return self._run_cli(["like", "dislike", "--video-id", video_id])

    # ── Share ────────────────────────────────────────────────────

    def create_share(self, video_id: str) -> dict[str, Any]:
        if not self.confirm_write:
            return {
                "requiresConfirmation": True,
                "message": "create_share is write; rerun with --confirm-write",
                "planned": {"video_id": video_id},
            }
        if self.backend == "fixture":
            self._fixture_get_video(video_id)
            links = self._load_fixture().setdefault("shareLinks", {})
            short_code = f"fixture-share-{len(links) + 1}"
            links[short_code] = {"shortCode": short_code, "videoId": video_id, "clickCount": 0}
            return links[short_code]
        return self._run_cli(["share", "create", "--video-id", video_id])

    def share_stats(self, short_code: str) -> dict[str, Any]:
        if self.backend == "fixture":
            link = self._load_fixture().get("shareLinks", {}).get(short_code, {})
            return link if link else {"error": f"share code not found: {short_code}"}
        return self._run_cli(["share", "stats", "--short-code", short_code])

    # ── Watch ────────────────────────────────────────────────────

    def watch_history(self, page: int = 0, page_size: int = 20) -> dict[str, Any]:
        if self.backend == "fixture":
            wh = self._load_fixture().get("watchHistory", {"list": [], "total": 0})
            return wh
        return self._run_cli(["watch", "history", "--page", str(page), "--page-size", str(page_size)])

    def get_watch_progress(self, video_id: str, client_id: str | None = None) -> dict[str, Any]:
        if self.backend == "fixture":
            return {"videoId": video_id, "progressSeconds": 0, "durationSeconds": 0}
        if not client_id:
            return {"error": "播放进度按设备存储，需要提供 client_id（观看设备标识）", "videoId": video_id}
        return self._run_cli(["watch", "progress", "--video-id", video_id, "--client-id", client_id])

    def clear_watch_history(self) -> dict[str, Any]:
        if not self.confirm_write:
            return {
                "requiresConfirmation": True,
                "message": "clear_watch_history is write, IRREVERSIBLE; rerun with --confirm-write",
            }
        if self.backend == "fixture":
            self._load_fixture()["watchHistory"] = {"list": [], "total": 0, "page": 0, "pageSize": 20}
            return {"cleared": True}
        return self._run_cli(["watch", "clear-history"])

    # ── Search ───────────────────────────────────────────────────

    def search_public_videos(
        self, keyword: str | None = None, category: str | None = None, page: int = 0, page_size: int = 20
    ) -> dict[str, Any]:
        if self.backend == "fixture":
            videos = [v for v in self._load_fixture().get("videos", []) if v.get("visibility") == "PUBLIC"]
            if keyword:
                kw = keyword.lower()
                videos = [
                    v
                    for v in videos
                    if kw in v.get("title", "").lower()
                    or kw in v.get("description", "").lower()
                    or any(kw in str(t).lower() for t in v.get("tags", []))
                ]
            if category:
                videos = [v for v in videos if v.get("category") == category]
            return {
                "content": videos[page * page_size: (page + 1) * page_size],
                "total": len(videos),
                "currentPage": page,
            }
        cmd = ["search", keyword or "", "--page", str(page), "--page-size", str(page_size)]
        if category:
            cmd += ["--category", category]
        return self._run_cli(cmd)

    def get_public_video_list(self, skip: int = 0, limit: int = 20, keyword: str | None = None) -> dict[str, Any]:
        if self.backend == "fixture":
            videos = [v for v in self._load_fixture().get("videos", []) if v.get("visibility") == "PUBLIC"]
            if keyword:
                kw = keyword.lower()
                videos = [
                    v
                    for v in videos
                    if kw in v.get("title", "").lower() or kw in v.get("description", "").lower()
                ]
            return {"list": videos[skip: skip + limit], "total": len(videos)}
        cmd = ["video", "public", "--skip", str(skip), "--limit", str(limit)]
        if keyword:
            cmd += ["--keyword", keyword]
        return self._run_cli(cmd)

    # ── Stats ────────────────────────────────────────────────────

    def get_traffic_stats(self, days: int = 7) -> dict[str, Any]:
        if self.backend == "fixture":
            return {"days": days, "data": []}
        end_ms = int(time.time() * 1000)
        start_ms = end_ms - days * 86_400_000
        return self._run_cli(["stats", "aggregate", "--start", str(start_ms), "--end", str(end_ms)])

    # ── Auth / User ──────────────────────────────────────────────

    def get_my_info(self) -> dict[str, Any]:
        if self.backend == "fixture":
            return {"id": "fixture-user", "nickname": "测试用户", "phone": "138****0000"}
        return self._run_cli(["auth", "me"])

    def get_my_profile(self) -> dict[str, Any]:
        if self.backend == "fixture":
            return {
                "id": "fixture-user",
                "nickname": "测试用户",
                "bio": "fixture 简介",
                "avatarUrl": None,
                "subscriberCount": 0,
                "videoCount": len(self._load_fixture().get("videos", [])),
                **self._load_fixture().get("profile", {}),
            }
        return self._run_cli(["user", "profile"])

    def update_profile(self, nickname: str | None = None, bio: str | None = None) -> dict[str, Any]:
        if not self.confirm_write:
            return {
                "requiresConfirmation": True,
                "message": "update_profile is write; rerun with --confirm-write",
                "planned": {"nickname": nickname, "bio": bio},
            }
        if nickname is None and bio is None:
            return {"error": "nickname 和 bio 至少提供一个"}
        if self.backend == "fixture":
            profile = self._load_fixture().setdefault(
                "profile", {"id": "fixture-user", "nickname": "测试用户", "bio": "fixture 简介"}
            )
            if nickname is not None:
                profile["nickname"] = nickname
            if bio is not None:
                profile["bio"] = bio
            return {"updated": True, "nickname": nickname, "bio": bio}
        cmd = ["user", "update-profile"]
        if nickname is not None:
            cmd += ["--nickname", nickname]
        if bio is not None:
            cmd += ["--bio", bio]
        return self._run_cli(cmd)

    # ── Channel / Subscription ───────────────────────────────────

    def subscribe_channel(self, channel_user_id: str) -> dict[str, Any]:
        if not self.confirm_write:
            return {
                "requiresConfirmation": True,
                "message": "subscribe_channel is write; rerun with --confirm-write",
                "planned": {"channel_user_id": channel_user_id},
            }
        if self.backend == "fixture":
            subscriptions = self._load_fixture().setdefault("subscriptions", [])
            if channel_user_id not in subscriptions:
                subscriptions.append(channel_user_id)
            return {"channelUserId": channel_user_id, "subscribed": True}
        return self._run_cli(["channel", "subscribe", "--user-id", channel_user_id])

    def unsubscribe_channel(self, channel_user_id: str) -> dict[str, Any]:
        if not self.confirm_write:
            return {
                "requiresConfirmation": True,
                "message": "unsubscribe_channel is write; rerun with --confirm-write",
                "planned": {"channel_user_id": channel_user_id},
            }
        if self.backend == "fixture":
            subscriptions = self._load_fixture().setdefault("subscriptions", [])
            if channel_user_id in subscriptions:
                subscriptions.remove(channel_user_id)
            return {"channelUserId": channel_user_id, "unsubscribed": True}
        return self._run_cli(["channel", "unsubscribe", "--user-id", channel_user_id])

    def get_my_subscriptions(self, skip: int = 0, limit: int = 20) -> dict[str, Any]:
        if self.backend == "fixture":
            subs = self._load_fixture().get("subscriptions", [])
            return {"list": subs[skip: skip + limit], "total": len(subs)}
        result = self._run_cli(["channel", "subscriptions", "--skip", str(skip), "--limit", str(limit)])
        # 服务端返回频道 userId 字符串数组，统一包成 {list, total}
        if isinstance(result, list):
            return {"list": result, "total": len(result)}
        return result

    def get_channel(self, user_id: str) -> dict[str, Any]:
        if self.backend == "fixture":
            return {
                "userId": user_id,
                "nickname": "fixture 频道",
                "avatarUrl": None,
                "bannerUrl": None,
                "bio": "",
                "subscriberCount": 0,
                "videoCount": 0,
                "isSubscribed": user_id in self._load_fixture().get("subscriptions", []),
            }
        return self._run_cli(["channel", "get", "--user-id", user_id])

    # ── YouTube ──────────────────────────────────────────────────

    def get_youtube_info(self, url: str) -> dict[str, Any]:
        if self.backend == "fixture":
            return {"url": url, "title": "YouTube 视频（fixture）", "duration": "10:00", "available": True}
        youtube_id = _extract_youtube_id(url)
        if not youtube_id:
            return {"error": f"无法解析 YouTube 视频 ID: {url}"}
        return self._run_cli(["youtube", "info", "--youtube-id", youtube_id])

    def transfer_youtube(self, url: str) -> dict[str, Any]:
        if not self.confirm_write:
            return {
                "requiresConfirmation": True,
                "message": "transfer_youtube is write; rerun with --confirm-write",
                "planned": {"url": url},
            }
        if self.backend == "fixture":
            video_id = f"fixture-youtube-{len(self._load_fixture().get('videos', [])) + 1}"
            self._load_fixture().setdefault("videos", []).append(
                {
                    "id": video_id,
                    "title": "YouTube 视频（fixture）",
                    "description": url,
                    "tags": ["YouTube"],
                    "category": "转存",
                    "status": "CREATED",
                    "visibility": "PRIVATE",
                    "watchCount": 0,
                    "trafficBytes": 0,
                    "createTime": "fixture-now",
                }
            )
            return {"videoId": video_id, "url": url, "transferred": True}
        youtube_id = _extract_youtube_id(url)
        if not youtube_id:
            return {"error": f"无法解析 YouTube 视频 ID: {url}"}
        return self._run_cli(["youtube", "transfer", "--youtube-id", youtube_id])

    # ── Helpers ──────────────────────────────────────────────────

    def resolve_videos(self, keyword: str, limit: int = 5) -> dict[str, Any]:
        """按标题关键词解析出候选视频，供 video_id 消歧（模型可见工具）。"""
        result = self.list_my_videos(keyword=keyword, limit=limit)
        if "error" in result:
            return result
        candidates = [
            {
                "videoId": v.get("id") or v.get("videoId"),
                "title": v.get("title"),
                "status": v.get("status"),
                "watchCount": v.get("watchCount"),
                "createTime": v.get("createTime"),
            }
            for v in result.get("list", [])
        ]
        return {"keyword": keyword, "total": result.get("total", len(candidates)), "candidates": candidates}

    def find_video_candidates(self, keyword: str | None, limit: int = 10) -> list[dict[str, Any]]:
        """Search user's own videos by keyword, returning candidate list for disambiguation."""
        if not keyword:
            return []
        result = self.list_my_videos(keyword=keyword, limit=limit)
        return result.get("list", []) if isinstance(result, dict) else []

    def all_my_videos(self, page_size: int = 100) -> list[dict[str, Any]]:
        first = self.list_my_videos(skip=0, limit=page_size)
        videos = list(first.get("list", []))
        total = int(first.get("total", len(videos)))
        while len(videos) < total:
            page = self.list_my_videos(skip=len(videos), limit=page_size)
            chunk = page.get("list", [])
            if not chunk:
                break
            videos.extend(chunk)
        return videos

    def snapshot_state(self) -> dict[str, Any] | None:
        """Return an isolated fixture snapshot for deterministic eval grading.

        The real CLI backend has no safe whole-account snapshot API, so callers
        must provide a purpose-built test backend snapshot for state assertions.
        """
        if self.backend != "fixture":
            return None
        return copy.deepcopy(self._load_fixture())

    def reset_fixture_state(self) -> None:
        """Reload fixture state so every eval trial starts from the same data."""
        if self.backend == "fixture":
            self._fixture_state = self._read_fixture()

    # ── Internal: CLI backend ────────────────────────────────────

    def _run_cli(self, args: list[str]) -> dict[str, Any]:
        cmd = [sys.executable, "-m", "video_cli.main", "--output", "json"]
        if self.base_url:
            cmd += ["--base-url", self.base_url]
        if self.token:
            cmd += ["--token", self.token]
        cmd += args

        env = os.environ.copy()
        cli_path = Path(__file__).resolve().parents[2] / "cli"
        env["PYTHONPATH"] = str(cli_path) + os.pathsep + env.get("PYTHONPATH", "")
        proc = subprocess.run(cmd, cwd=cli_path, env=env, text=True, capture_output=True, timeout=60)
        if proc.returncode != 0:
            detail = proc.stderr.strip() or proc.stdout.strip()
            raise RuntimeError(f"video-cli failed ({proc.returncode}): {detail}")
        data = json.loads(proc.stdout)
        if isinstance(data, dict) and data.get("success") is True and "data" in data:
            return data["data"]
        return data

    # ── Internal: HTTP upload ────────────────────────────────────

    def _upload_via_http(
        self, file_path: str, title: str | None = None, visibility: str | None = None
    ) -> dict[str, Any]:
        try:
            import oss2
        except ImportError:
            raise RuntimeError("oss2 is required for real uploads; run `uv sync`")

        path = Path(file_path).expanduser().resolve()
        if not path.exists():
            raise FileNotFoundError(str(path))

        base_url = (self.base_url or os.getenv("VIDEO_CLI_BASE_URL") or "http://localhost:5022").rstrip("/")
        token = self.token or os.getenv("VIDEO_CLI_TOKEN")
        headers = {"Content-Type": "application/json"}
        if token:
            headers["token"] = token

        create_body = {"rawFilename": path.name, "size": path.stat().st_size, "videoType": "USER_UPLOAD"}
        create_data = _api_post(f"{base_url}/video/create", create_body, headers)
        file_id = create_data["fileId"]
        video_id = create_data["videoId"]

        creds = _api_get(f"{base_url}/file/getUploadCredentials", {"fileId": file_id}, headers)
        auth = oss2.StsAuth(creds["accessKeyId"], creds["secretKey"], creds["sessionToken"])
        bucket = oss2.Bucket(auth, creds["endpoint"], creds["bucket"])
        bucket.put_object_from_file(creds["key"], str(path))

        _api_get(f"{base_url}/file/uploadFinish", {"fileId": file_id}, headers)
        _api_get(f"{base_url}/video/rawFileUploadFinish", {"videoId": video_id}, headers)

        if title or visibility:
            update_body = {"id": video_id, "title": title or path.stem, "description": ""}
            if visibility:
                update_body["visibility"] = visibility
            _api_post(f"{base_url}/video/updateInfo", update_body, headers)

        return {
            "fileId": file_id,
            "videoId": video_id,
            "watchId": create_data.get("watchId"),
            "watchUrl": create_data.get("watchUrl"),
        }

    # ── Internal: Fixture backend ────────────────────────────────

    def _fixture_list_videos(self, keyword: str | None, skip: int, limit: int) -> dict[str, Any]:
        data = self._load_fixture()
        videos = list(data.get("videos", []))
        if keyword:
            kw = keyword.lower()
            videos = [
                v
                for v in videos
                if kw in str(v.get("title", "")).lower()
                or kw in str(v.get("description", "")).lower()
                or any(kw in str(t).lower() for t in v.get("tags", []))
            ]
        videos.sort(key=lambda item: item.get("createTime", ""), reverse=True)
        return {"list": videos[skip: skip + limit], "total": len(videos)}

    def _fixture_get_video(self, video_id: str) -> dict[str, Any]:
        for v in self._load_fixture().get("videos", []):
            if v.get("id") == video_id:
                return v
        raise KeyError(f"video not found: {video_id}")

    def _fixture_playlist(self, playlist_id: str) -> dict[str, Any]:
        for playlist in self._load_fixture().get("playlists", []):
            if playlist.get("id") == playlist_id:
                return playlist
        raise KeyError(f"playlist not found: {playlist_id}")

    def _fixture_comment(self, comment_id: str) -> dict[str, Any]:
        for comments in self._load_fixture().get("comments", {}).values():
            for comment in comments:
                if comment.get("id") == comment_id:
                    return comment
        raise KeyError(f"comment not found: {comment_id}")

    def _load_fixture(self) -> dict[str, Any]:
        if self._fixture_state is None:
            self._fixture_state = self._read_fixture()
        return self._fixture_state

    def _read_fixture(self) -> dict[str, Any]:
        cfg = get_config()
        default_path = Path(__file__).resolve().parents[1] / "fixtures" / "videos.json"
        path = Path(self.fixture_path or cfg.fixture_path or default_path)
        with path.open(encoding="utf-8") as f:
            return json.load(f)

    def _record(self, name: str, args: dict[str, Any], result: Any) -> None:
        self.trace.append(ToolCall(name=name, args=args, result=result))


# ── HTTP helpers ──────────────────────────────────────────────────

_YOUTUBE_ID_RE = re.compile(r"^[A-Za-z0-9_-]{11}$")
_YOUTUBE_URL_RE = re.compile(
    r"(?:youtube\.com/(?:watch\?[^#]*v=|shorts/|embed/|live/)|youtu\.be/)([A-Za-z0-9_-]{11})"
)


def _extract_youtube_id(value: str) -> str | None:
    value = value.strip()
    if _YOUTUBE_ID_RE.match(value):
        return value
    match = _YOUTUBE_URL_RE.search(value)
    return match.group(1) if match else None


def _api_get(url: str, params: dict[str, Any], headers: dict[str, str]) -> dict[str, Any]:
    resp = requests.get(url, params=params, headers=headers, timeout=60)
    return _unwrap(resp)


def _api_post(url: str, body: dict[str, Any], headers: dict[str, str]) -> dict[str, Any]:
    resp = requests.post(url, json=body, headers=headers, timeout=60)
    return _unwrap(resp)


def _unwrap(resp: requests.Response) -> dict[str, Any]:
    resp.raise_for_status()
    data = resp.json()
    if data.get("code") != 0:
        raise RuntimeError(data.get("message", "API request failed"))
    return data.get("data") or {}
