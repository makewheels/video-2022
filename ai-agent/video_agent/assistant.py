"""Conversation executor for video-2022 natural-language requests."""

from __future__ import annotations

from typing import Any

from .planner import Plan, plan_query
from .tools import VideoTools


class VideoAssistant:
    def __init__(self, tools: VideoTools, planner: str = "heuristic") -> None:
        self.tools = tools
        self.planner = planner

    def answer(self, query: str) -> dict[str, Any]:
        self.tools.trace.clear()
        plan = plan_query(query, self.planner)
        answer = self._execute(plan)
        return {
            "query": query,
            "intent": plan.intent,
            "planner": plan.source,
            "answer": answer,
            "trace": [
                {"name": call.name, "args": call.args, "result": call.result}
                for call in self.tools.trace
            ],
        }

    def _execute(self, plan: Plan) -> str:
        if plan.intent == "count_my_videos":
            result = self.tools.list_my_videos(skip=0, limit=1)
            return f"你一共上传了 {result.get('total', 0)} 个视频。"

        if plan.intent == "first_uploaded_video":
            videos = self.tools.all_my_videos()
            if not videos:
                return "你还没有上传过视频。"
            video = min(videos, key=lambda v: v.get("createTime", ""))
            return _format_video("你最早上传的视频是", video)

        if plan.intent == "latest_uploaded_video":
            videos = self.tools.all_my_videos()
            if not videos:
                return "你还没有上传过视频。"
            video = max(videos, key=lambda v: v.get("createTime", ""))
            return _format_video("你最近上传的视频是", video)

        if plan.intent == "video_watch_count":
            video = self._resolve_single_video(plan.args.get("keyword"))
            if isinstance(video, str):
                return video
            detail = self.tools.get_video_detail(video["id"])
            return f"《{detail.get('title', detail.get('id'))}》当前播放量是 {detail.get('watchCount', 0)}。视频 ID：{detail.get('id')}。"

        if plan.intent == "video_status":
            video = self._resolve_single_video(plan.args.get("keyword"))
            if isinstance(video, str):
                return video
            status = self.tools.get_video_status(video["id"])
            return f"《{video.get('title', video.get('id'))}》当前状态是 {status.get('status')}。"

        if plan.intent == "video_traffic":
            video = self._resolve_single_video(plan.args.get("keyword"))
            if isinstance(video, str):
                return video
            traffic = self.tools.get_video_traffic(video["id"])
            bytes_value = traffic.get("trafficBytes") or traffic.get("bytes") or traffic.get("traffic") or 0
            return f"《{video.get('title', video.get('id'))}》当前流量消耗是 {_format_bytes(bytes_value)}。"

        if plan.intent == "watch_history":
            history = self.tools.watch_history(page=0, page_size=5)
            items = history.get("list", [])
            if not items:
                return "当前没有观看历史。"
            titles = [item.get("title") or item.get("videoId") for item in items[:5]]
            return "最近观看历史：" + "，".join(titles) + f"。共 {history.get('total', len(items))} 条。"

        if plan.intent == "search_public_videos":
            result = self.tools.search_public_videos(plan.args.get("keyword"), plan.args.get("category"), page=0, page_size=5)
            videos = result.get("content") or result.get("list") or []
            if not videos:
                return "没有找到匹配的公开视频。"
            names = [f"《{v.get('title', v.get('id'))}》" for v in videos]
            return f"找到 {result.get('total', len(videos))} 个公开视频：" + "，".join(names) + "。"

        if plan.intent == "comment_count":
            video = self._resolve_single_video(plan.args.get("keyword"))
            if isinstance(video, str):
                return video
            result = self.tools.comment_count(video["id"])
            return f"《{video.get('title', video.get('id'))}》有 {result.get('count', result.get('total', 0))} 条评论。"

        if plan.intent == "list_comments":
            video = self._resolve_single_video(plan.args.get("keyword"))
            if isinstance(video, str):
                return video
            result = self.tools.list_comments(video["id"], skip=0, limit=5)
            comments = result.get("list", []) if isinstance(result, dict) else result
            if not comments:
                return f"《{video.get('title', video.get('id'))}》还没有评论。"
            snippets = [str(c.get("content", ""))[:30] for c in comments[:5]]
            return f"《{video.get('title', video.get('id'))}》最近评论：" + "；".join(snippets) + "。"

        if plan.intent == "list_playlists":
            result = self.tools.list_playlists(skip=0, limit=10)
            playlists = result.get("list", []) if isinstance(result, dict) else result
            if not playlists:
                return "你还没有播放列表。"
            names = [f"《{p.get('title', p.get('id'))}》({p.get('videoCount', len(p.get('videoIds', [])))} 个视频)" for p in playlists]
            return "你的播放列表：" + "，".join(names) + "。"

        if plan.intent == "unread_notification_count":
            result = self.tools.unread_notification_count()
            return f"你有 {result.get('count', result.get('unreadCount', 0))} 条未读通知。"

        if plan.intent == "list_notifications":
            result = self.tools.list_notifications(page=0, page_size=5)
            items = result.get("list", []) if isinstance(result, dict) else result
            if not items:
                return "当前没有通知。"
            snippets = [str(n.get("content") or n.get("type") or n.get("id"))[:40] for n in items[:5]]
            return "最近通知：" + "；".join(snippets) + "。"

        if plan.intent == "like_status":
            video = self._resolve_single_video(plan.args.get("keyword"))
            if isinstance(video, str):
                return video
            status = self.tools.like_status(video["id"])
            if status.get("liked"):
                return f"你已经点赞《{video.get('title', video.get('id'))}》。"
            if status.get("disliked"):
                return f"你已经点踩《{video.get('title', video.get('id'))}》。"
            return f"你还没有对《{video.get('title', video.get('id'))}》点赞或点踩。"

        if plan.intent == "share_stats":
            result = self.tools.share_stats(plan.args.get("short_code"))
            if result.get("error"):
                return "你要查哪个分享短码？"
            return f"分享短码 {result.get('shortCode')} 的点击量是 {result.get('clickCount', 0)}。"

        if plan.intent == "create_share":
            video = self._resolve_single_video(plan.args.get("keyword"))
            if isinstance(video, str):
                return video
            result = self.tools.create_share(video["id"])
            if result.get("requiresConfirmation"):
                return f"创建分享链接是写操作，需要确认后执行。计划为《{video.get('title')}》创建分享链接。"
            return f"分享链接已创建，短码：{result.get('shortCode')}。"

        if plan.intent == "upload_video":
            data = self.tools.upload_video(
                file_path=plan.args.get("file_path"),
                title=plan.args.get("title"),
                visibility=plan.args.get("visibility"),
            )
            if data.get("requiresConfirmation"):
                return f"上传是写操作，需要确认后执行。计划上传：{data.get('planned')}。"
            return f"上传已提交。videoId={data.get('videoId')}，watchUrl={data.get('watchUrl')}。"

        result = self.tools.list_my_videos(keyword=plan.args.get("keyword"), skip=0, limit=10)
        videos = result.get("list", [])
        if not videos:
            return "没有找到匹配的视频。"
        names = [f"《{v.get('title', v.get('id'))}》" for v in videos[:5]]
        return f"找到 {result.get('total', len(videos))} 个视频：" + "，".join(names) + "。"

    def _resolve_single_video(self, keyword: str | None) -> dict[str, Any] | str:
        if not keyword:
            return "你要查哪个视频？请提供标题或视频 ID。"
        candidates = self.tools.find_video_candidates(keyword)
        if not candidates:
            return f"没有找到和“{keyword}”匹配的视频。"
        if len(candidates) > 1:
            names = [f"{idx + 1}. 《{v.get('title', v.get('id'))}》（ID：{v.get('id')}）" for idx, v in enumerate(candidates[:5])]
            return "找到多个匹配视频，请指定一个：" + "；".join(names)
        return candidates[0]


def _format_video(prefix: str, video: dict[str, Any]) -> str:
    return (
        f"{prefix}《{video.get('title', video.get('id'))}》，"
        f"上传时间：{video.get('createTimeString') or video.get('createTime')}，"
        f"播放量：{video.get('watchCount', 0)}，视频 ID：{video.get('id')}。"
    )


def _format_bytes(value: Any) -> str:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return str(value)
    units = ["B", "KB", "MB", "GB", "TB"]
    idx = 0
    while number >= 1024 and idx < len(units) - 1:
        number /= 1024
        idx += 1
    if idx == 0:
        return f"{int(number)} {units[idx]}"
    return f"{number:.2f} {units[idx]}"
