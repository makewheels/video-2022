"""Tool layer for the video assistant."""

from __future__ import annotations

import json
import os
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import requests


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

    def list_my_videos(self, skip: int = 0, limit: int = 20, keyword: str | None = None) -> dict[str, Any]:
        args = {"skip": skip, "limit": limit, "keyword": keyword}
        if self.backend == "fixture":
            result = self._fixture_list(skip, limit, keyword)
        else:
            cmd = ["video", "list", "--skip", str(skip), "--limit", str(limit)]
            if keyword:
                cmd += ["--keyword", keyword]
            result = self._run_video_cli(cmd)
        return self._record("list_my_videos", args, result)

    def get_video_detail(self, video_id: str) -> dict[str, Any]:
        args = {"video_id": video_id}
        if self.backend == "fixture":
            result = self._fixture_get(video_id)
        else:
            result = self._run_video_cli(["video", "detail", "--id", video_id])
        return self._record("get_video_detail", args, result)

    def get_video_status(self, video_id: str) -> dict[str, Any]:
        args = {"video_id": video_id}
        if self.backend == "fixture":
            video = self._fixture_get(video_id)
            result = {"videoId": video_id, "status": video.get("status"), "isReady": video.get("status") == "READY"}
        else:
            result = self._run_video_cli(["video", "status", "--id", video_id])
        return self._record("get_video_status", args, result)

    def get_video_traffic(self, video_id: str) -> dict[str, Any]:
        args = {"video_id": video_id}
        if self.backend == "fixture":
            video = self._fixture_get(video_id)
            result = {"videoId": video_id, "trafficBytes": video.get("trafficBytes", 0)}
        else:
            result = self._run_video_cli(["stats", "traffic", "--video-id", video_id])
        return self._record("get_video_traffic", args, result)

    def watch_history(self, page: int = 0, page_size: int = 20) -> dict[str, Any]:
        args = {"page": page, "page_size": page_size}
        if self.backend == "fixture":
            data = self._load_fixture()
            result = data.get("watchHistory", {"list": [], "total": 0, "page": page, "pageSize": page_size})
        else:
            result = self._run_video_cli(["watch", "history", "--page", str(page), "--page-size", str(page_size)])
        return self._record("watch_history", args, result)

    def search_public_videos(self, keyword: str | None = None, category: str | None = None, page: int = 0, page_size: int = 20) -> dict[str, Any]:
        args = {"keyword": keyword, "category": category, "page": page, "page_size": page_size}
        if self.backend == "fixture":
            videos = [v for v in self._load_fixture().get("videos", []) if v.get("visibility") == "PUBLIC"]
            if keyword:
                needle = keyword.lower()
                videos = [v for v in videos if needle in v.get("title", "").lower() or needle in v.get("description", "").lower() or any(needle in str(tag).lower() for tag in v.get("tags", []))]
            if category:
                videos = [v for v in videos if v.get("category") == category]
            result = {"content": videos[page * page_size: (page + 1) * page_size], "total": len(videos), "currentPage": page}
        else:
            cmd = ["search", keyword or "", "--page", str(page), "--page-size", str(page_size)]
            if category:
                cmd += ["--category", category]
            result = self._run_video_cli(cmd)
        return self._record("search_public_videos", args, result)

    def comment_count(self, video_id: str) -> dict[str, Any]:
        args = {"video_id": video_id}
        if self.backend == "fixture":
            count = len(self._load_fixture().get("comments", {}).get(video_id, []))
            result = {"videoId": video_id, "count": count}
        else:
            result = self._run_video_cli(["comment", "count", "--video-id", video_id])
        return self._record("comment_count", args, result)

    def list_comments(self, video_id: str, skip: int = 0, limit: int = 20) -> dict[str, Any]:
        args = {"video_id": video_id, "skip": skip, "limit": limit}
        if self.backend == "fixture":
            comments = self._load_fixture().get("comments", {}).get(video_id, [])
            result = {"list": comments[skip: skip + limit], "total": len(comments)}
        else:
            result = self._run_video_cli(["comment", "list", "--video-id", video_id, "--skip", str(skip), "--limit", str(limit)])
        return self._record("list_comments", args, result)

    def list_playlists(self, skip: int = 0, limit: int = 20) -> dict[str, Any]:
        args = {"skip": skip, "limit": limit}
        if self.backend == "fixture":
            playlists = self._load_fixture().get("playlists", [])
            result = {"list": playlists[skip: skip + limit], "total": len(playlists)}
        else:
            result = self._run_video_cli(["playlist", "list", "--skip", str(skip), "--limit", str(limit)])
        return self._record("list_playlists", args, result)

    def unread_notification_count(self) -> dict[str, Any]:
        args: dict[str, Any] = {}
        if self.backend == "fixture":
            notifications = self._load_fixture().get("notifications", [])
            result = {"count": len([n for n in notifications if not n.get("read")])}
        else:
            result = self._run_video_cli(["notification", "unread-count"])
        return self._record("unread_notification_count", args, result)

    def list_notifications(self, page: int = 0, page_size: int = 20) -> dict[str, Any]:
        args = {"page": page, "page_size": page_size}
        if self.backend == "fixture":
            notifications = self._load_fixture().get("notifications", [])
            result = {"list": notifications[page * page_size: (page + 1) * page_size], "total": len(notifications)}
        else:
            result = self._run_video_cli(["notification", "list", "--page", str(page), "--page-size", str(page_size)])
        return self._record("list_notifications", args, result)

    def like_status(self, video_id: str) -> dict[str, Any]:
        args = {"video_id": video_id}
        if self.backend == "fixture":
            result = self._load_fixture().get("likeStatus", {}).get(video_id, {"liked": False, "disliked": False})
        else:
            result = self._run_video_cli(["like", "status", "--video-id", video_id])
        return self._record("like_status", args, result)

    def share_stats(self, short_code: str | None) -> dict[str, Any]:
        args = {"short_code": short_code}
        if not short_code:
            result = {"error": "short_code is required"}
        elif self.backend == "fixture":
            result = self._load_fixture().get("shareLinks", {}).get(short_code, {"shortCode": short_code, "clickCount": 0})
        else:
            result = self._run_video_cli(["share", "stats", "--short-code", short_code])
        return self._record("share_stats", args, result)

    def create_share(self, video_id: str) -> dict[str, Any]:
        args = {"video_id": video_id}
        if not self.confirm_write:
            result = {"requiresConfirmation": True, "message": "create_share is a write operation; rerun with --confirm-write to execute it", "planned": args}
        elif self.backend == "fixture":
            result = {"shortCode": "fixture-share", "videoId": video_id, "clickCount": 0}
        else:
            result = self._run_video_cli(["share", "create", "--video-id", video_id])
        return self._record("create_share", args, result)

    def upload_video(self, file_path: str | None, title: str | None = None, visibility: str | None = None) -> dict[str, Any]:
        args = {"file_path": file_path, "title": title, "visibility": visibility}
        if not self.confirm_write:
            result = {
                "requiresConfirmation": True,
                "message": "upload_video is a write operation; rerun with --confirm-write to execute it",
                "planned": args,
            }
            return self._record("upload_video", args, result)
        if not file_path:
            raise ValueError("file_path is required for upload")
        if self.backend == "fixture":
            result = {"videoId": "fixture-upload-id", "status": "CREATED", "planned": args}
        else:
            result = self._upload_via_http(file_path, title=title, visibility=visibility)
        return self._record("upload_video", args, result)

    def find_video_candidates(self, keyword: str | None, limit: int = 10) -> list[dict[str, Any]]:
        if not keyword:
            return []
        result = self.list_my_videos(skip=0, limit=limit, keyword=keyword)
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

    def _run_video_cli(self, args: list[str]) -> dict[str, Any]:
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

    def _upload_via_http(self, file_path: str, title: str | None = None, visibility: str | None = None) -> dict[str, Any]:
        try:
            import oss2
        except ImportError as exc:
            raise RuntimeError("oss2 is required for real uploads; run `pip install -r requirements.txt`") from exc

        path = Path(file_path).expanduser().resolve()
        if not path.exists():
            raise FileNotFoundError(str(path))

        base_url = (self.base_url or os.getenv("VIDEO_CLI_BASE_URL") or "http://localhost:5022").rstrip("/")
        headers = {"Content-Type": "application/json"}
        token = self.token or os.getenv("VIDEO_CLI_TOKEN")
        if token:
            headers["token"] = token

        create_body = {
            "rawFilename": path.name,
            "size": path.stat().st_size,
            "videoType": "USER_UPLOAD",
        }
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

    def _fixture_list(self, skip: int, limit: int, keyword: str | None) -> dict[str, Any]:
        data = self._load_fixture()
        videos = list(data.get("videos", []))
        if keyword:
            needle = keyword.lower()
            videos = [
                v for v in videos
                if needle in str(v.get("title", "")).lower()
                or needle in str(v.get("description", "")).lower()
                or any(needle in str(tag).lower() for tag in v.get("tags", []))
            ]
        videos.sort(key=lambda item: item.get("createTime", ""), reverse=True)
        return {"list": videos[skip: skip + limit], "total": len(videos)}

    def _fixture_get(self, video_id: str) -> dict[str, Any]:
        for video in self._load_fixture().get("videos", []):
            if video.get("id") == video_id:
                return video
        raise KeyError(f"video not found: {video_id}")

    def _load_fixture(self) -> dict[str, Any]:
        path = Path(self.fixture_path or Path(__file__).resolve().parents[1] / "fixtures" / "videos.json")
        with path.open(encoding="utf-8") as f:
            return json.load(f)

    def _record(self, name: str, args: dict[str, Any], result: Any) -> Any:
        self.trace.append(ToolCall(name=name, args=args, result=result))
        return result


def _api_get(url: str, params: dict[str, Any], headers: dict[str, str]) -> dict[str, Any]:
    resp = requests.get(url, params=params, headers=headers, timeout=60)
    return _unwrap_response(resp)


def _api_post(url: str, body: dict[str, Any], headers: dict[str, str]) -> dict[str, Any]:
    resp = requests.post(url, json=body, headers=headers, timeout=60)
    return _unwrap_response(resp)


def _unwrap_response(resp: requests.Response) -> dict[str, Any]:
    resp.raise_for_status()
    body = resp.json()
    if body.get("code") != 0:
        raise RuntimeError(body.get("message", "API request failed"))
    return body.get("data") or {}
