"""Tool execution layer — maps tool names to implementations.

Supports two backends:
- fixture:  offline test data from fixtures/videos.json
- cli:      real backend via video-cli
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import requests

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

    # ── Dispatch ─────────────────────────────────────────────────

    def execute(self, name: str, args: dict[str, Any]) -> Any:
        """Execute a tool by name and return its result."""
        method = getattr(self, name, None)
        if method is None:
            return {"error": f"Unknown tool: {name}"}
        try:
            result = method(**args)
            self._record(name, args, result)
            return result
        except Exception as exc:
            error_result = {"error": str(exc), "tool": name}
            self._record(name, args, error_result)
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

    def update_video(self, video_id: str, title: str | None = None, description: str | None = None, visibility: str | None = None) -> dict[str, Any]:
        if not self.confirm_write:
            return {"requiresConfirmation": True, "message": "update_video is write; rerun with --confirm-write", "planned": {"video_id": video_id, "title": title, "visibility": visibility}}
        if self.backend == "fixture":
            return {"videoId": video_id, "updated": True}
        self._run_cli(["video", "update", "--id", video_id, "--title", title or "", "--description", description or "", "--visibility", visibility or ""])
        return {"videoId": video_id, "updated": True}

    def delete_video(self, video_id: str) -> dict[str, Any]:
        if not self.confirm_write:
            return {"requiresConfirmation": True, "message": "delete_video is write, IRREVERSIBLE; rerun with --confirm-write", "planned": {"video_id": video_id}}
        if self.backend == "fixture":
            return {"videoId": video_id, "deleted": True}
        self._run_cli(["video", "delete", "--id", video_id])
        return {"videoId": video_id, "deleted": True}

    def upload_video(self, file_path: str, title: str | None = None, visibility: str | None = None) -> dict[str, Any]:
        if not self.confirm_write:
            return {"requiresConfirmation": True, "message": "upload_video is write; rerun with --confirm-write", "planned": {"file_path": file_path, "title": title, "visibility": visibility}}
        if self.backend == "fixture":
            return {"videoId": "fixture-upload-id", "status": "CREATED"}
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
            return {"requiresConfirmation": True, "message": "add_comment is write; rerun with --confirm-write", "planned": {"video_id": video_id, "content": content[:50]}}
        if self.backend == "fixture":
            return {"commentId": "fixture-comment-id", "videoId": video_id, "added": True}
        cmd = ["comment", "add", "--video-id", video_id, "--content", content]
        if parent_comment_id:
            cmd += ["--parent-id", parent_comment_id]
        return self._run_cli(cmd)

    def delete_comment(self, comment_id: str) -> dict[str, Any]:
        if not self.confirm_write:
            return {"requiresConfirmation": True, "message": "delete_comment is write, IRREVERSIBLE; rerun with --confirm-write", "planned": {"comment_id": comment_id}}
        if self.backend == "fixture":
            return {"commentId": comment_id, "deleted": True}
        self._run_cli(["comment", "delete", "--id", comment_id])
        return {"commentId": comment_id, "deleted": True}

    def like_comment(self, comment_id: str) -> dict[str, Any]:
        if not self.confirm_write:
            return {"requiresConfirmation": True, "message": "like_comment is write; rerun with --confirm-write", "planned": {"comment_id": comment_id}}
        if self.backend == "fixture":
            return {"commentId": comment_id, "liked": True}
        return self._run_cli(["comment", "like", "--id", comment_id])

    def comment_replies(self, comment_id: str) -> dict[str, Any]:
        if self.backend == "fixture":
            return {"list": [], "total": 0, "parentCommentId": comment_id}
        return self._run_cli(["comment", "replies", "--id", comment_id])

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
            return {"requiresConfirmation": True, "message": "create_playlist is write; rerun with --confirm-write", "planned": {"title": title, "description": description}}
        if self.backend == "fixture":
            return {"playlistId": "fixture-playlist-id", "title": title, "created": True}
        cmd = ["playlist", "create", "--title", title]
        if description:
            cmd += ["--description", description]
        return self._run_cli(cmd)

    def add_video_to_playlist(self, playlist_id: str, video_id: str) -> dict[str, Any]:
        if not self.confirm_write:
            return {"requiresConfirmation": True, "message": "add_video_to_playlist is write; rerun with --confirm-write"}
        if self.backend == "fixture":
            return {"playlistId": playlist_id, "videoId": video_id, "added": True}
        return self._run_cli(["playlist", "add-item", "--playlist-id", playlist_id, "--video-id", video_id])

    def remove_video_from_playlist(self, playlist_id: str, video_id: str) -> dict[str, Any]:
        if not self.confirm_write:
            return {"requiresConfirmation": True, "message": "remove_video_from_playlist is write; rerun with --confirm-write"}
        if self.backend == "fixture":
            return {"playlistId": playlist_id, "videoId": video_id, "removed": True}
        return self._run_cli(["playlist", "delete-item", "--playlist-id", playlist_id, "--video-id", video_id])

    def delete_playlist(self, playlist_id: str) -> dict[str, Any]:
        if not self.confirm_write:
            return {"requiresConfirmation": True, "message": "delete_playlist is write, IRREVERSIBLE; rerun with --confirm-write", "planned": {"playlist_id": playlist_id}}
        if self.backend == "fixture":
            return {"playlistId": playlist_id, "deleted": True}
        self._run_cli(["playlist", "delete", "--id", playlist_id])
        return {"playlistId": playlist_id, "deleted": True}

    def update_playlist(self, playlist_id: str, title: str | None = None, description: str | None = None) -> dict[str, Any]:
        if not self.confirm_write:
            return {"requiresConfirmation": True, "message": "update_playlist is write; rerun with --confirm-write"}
        if self.backend == "fixture":
            return {"playlistId": playlist_id, "updated": True}
        cmd = ["playlist", "update", "--id", playlist_id]
        if title:
            cmd += ["--title", title]
        if description:
            cmd += ["--description", description]
        return self._run_cli(cmd)

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
            return {"requiresConfirmation": True, "message": "mark_notification_read is write; rerun with --confirm-write"}
        if self.backend == "fixture":
            return {"notificationId": notification_id, "read": True}
        return self._run_cli(["notification", "read", "--id", notification_id])

    def mark_all_notifications_read(self) -> dict[str, Any]:
        if not self.confirm_write:
            return {"requiresConfirmation": True, "message": "mark_all_notifications_read is write; rerun with --confirm-write"}
        if self.backend == "fixture":
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
            return {"videoId": video_id, "liked": True}
        return self._run_cli(["like", "like", "--video-id", video_id])

    def dislike_video(self, video_id: str) -> dict[str, Any]:
        if not self.confirm_write:
            return {"requiresConfirmation": True, "message": "dislike_video is write; rerun with --confirm-write"}
        if self.backend == "fixture":
            return {"videoId": video_id, "disliked": True}
        return self._run_cli(["like", "dislike", "--video-id", video_id])

    # ── Share ────────────────────────────────────────────────────

    def create_share(self, video_id: str) -> dict[str, Any]:
        if not self.confirm_write:
            return {"requiresConfirmation": True, "message": "create_share is write; rerun with --confirm-write", "planned": {"video_id": video_id}}
        if self.backend == "fixture":
            return {"shortCode": "fixture-share", "videoId": video_id, "clickCount": 0}
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

    def get_watch_progress(self, video_id: str) -> dict[str, Any]:
        if self.backend == "fixture":
            return {"videoId": video_id, "progressSeconds": 0, "durationSeconds": 0}
        return self._run_cli(["watch", "progress", "--video-id", video_id])

    def clear_watch_history(self) -> dict[str, Any]:
        if not self.confirm_write:
            return {"requiresConfirmation": True, "message": "clear_watch_history is write, IRREVERSIBLE; rerun with --confirm-write"}
        if self.backend == "fixture":
            return {"cleared": True}
        return self._run_cli(["watch", "clear-history"])

    # ── Search ───────────────────────────────────────────────────

    def search_public_videos(self, keyword: str | None = None, category: str | None = None, page: int = 0, page_size: int = 20) -> dict[str, Any]:
        if self.backend == "fixture":
            videos = [v for v in self._load_fixture().get("videos", []) if v.get("visibility") == "PUBLIC"]
            if keyword:
                kw = keyword.lower()
                videos = [v for v in videos if kw in v.get("title", "").lower() or kw in v.get("description", "").lower() or any(kw in str(t).lower() for t in v.get("tags", []))]
            if category:
                videos = [v for v in videos if v.get("category") == category]
            return {"content": videos[page * page_size: (page + 1) * page_size], "total": len(videos), "currentPage": page}
        cmd = ["search", keyword or "", "--page", str(page), "--page-size", str(page_size)]
        if category:
            cmd += ["--category", category]
        return self._run_cli(cmd)

    # ── Stats ────────────────────────────────────────────────────

    def get_traffic_stats(self, days: int = 7) -> dict[str, Any]:
        if self.backend == "fixture":
            return {"days": days, "data": []}
        return self._run_cli(["stats", "aggregate", "--days", str(days)])

    # ── Auth / User ──────────────────────────────────────────────

    def get_my_info(self) -> dict[str, Any]:
        if self.backend == "fixture":
            return {"id": "fixture-user", "nickname": "测试用户", "phone": "138****0000"}
        return self._run_cli(["auth", "me"])

    # ── YouTube ──────────────────────────────────────────────────

    def get_youtube_info(self, url: str) -> dict[str, Any]:
        if self.backend == "fixture":
            return {"url": url, "title": "YouTube 视频（fixture）", "duration": "10:00", "available": True}
        return self._run_cli(["youtube", "info", url])

    def transfer_youtube(self, url: str) -> dict[str, Any]:
        if not self.confirm_write:
            return {"requiresConfirmation": True, "message": "transfer_youtube is write; rerun with --confirm-write", "planned": {"url": url}}
        if self.backend == "fixture":
            return {"videoId": "fixture-youtube-video", "url": url, "transferred": True}
        return self._run_cli(["youtube", "transfer", url])

    # ── Helpers ──────────────────────────────────────────────────

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

    def _upload_via_http(self, file_path: str, title: str | None = None, visibility: str | None = None) -> dict[str, Any]:
        try:
            import oss2
        except ImportError:
            raise RuntimeError("oss2 is required for real uploads; run `pip install -r requirements.txt`")

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

        return {"fileId": file_id, "videoId": video_id, "watchId": create_data.get("watchId"), "watchUrl": create_data.get("watchUrl")}

    # ── Internal: Fixture backend ────────────────────────────────

    def _fixture_list_videos(self, keyword: str | None, skip: int, limit: int) -> dict[str, Any]:
        data = self._load_fixture()
        videos = list(data.get("videos", []))
        if keyword:
            kw = keyword.lower()
            videos = [v for v in videos if kw in str(v.get("title", "")).lower() or kw in str(v.get("description", "")).lower() or any(kw in str(t).lower() for t in v.get("tags", []))]
        videos.sort(key=lambda item: item.get("createTime", ""), reverse=True)
        return {"list": videos[skip: skip + limit], "total": len(videos)}

    def _fixture_get_video(self, video_id: str) -> dict[str, Any]:
        for v in self._load_fixture().get("videos", []):
            if v.get("id") == video_id:
                return v
        raise KeyError(f"video not found: {video_id}")

    def _load_fixture(self) -> dict[str, Any]:
        cfg = get_config()
        path = Path(self.fixture_path or cfg.fixture_path or Path(__file__).resolve().parents[1] / "fixtures" / "videos.json")
        with path.open(encoding="utf-8") as f:
            return json.load(f)

    def _record(self, name: str, args: dict[str, Any], result: Any) -> None:
        self.trace.append(ToolCall(name=name, args=args, result=result))


# ── HTTP helpers ──────────────────────────────────────────────────

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
