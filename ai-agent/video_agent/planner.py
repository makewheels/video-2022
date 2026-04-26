"""Intent planning for natural-language video requests."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from typing import Any

@dataclass
class Plan:
    intent: str
    args: dict[str, Any] = field(default_factory=dict)
    confidence: float = 0.0
    needs_confirmation: bool = False
    source: str = "heuristic"


def plan_query(query: str, planner: str = "heuristic") -> Plan:
    if planner in {"llm", "minimax"}:
        try:
            from .llm import OpenAICompatiblePlannerClient

            plan = OpenAICompatiblePlannerClient().plan(query)
            return plan
        except Exception:
            return heuristic_plan(query)
    return heuristic_plan(query)


def heuristic_plan(query: str) -> Plan:
    q = query.strip()
    if _is_upload(q):
        return Plan(
            intent="upload_video",
            args=_extract_upload_args(q),
            confidence=0.72,
            needs_confirmation=True,
        )

    if _contains_any(q, ["几个视频", "多少个视频", "视频数量", "上传了几个", "上传了多少"]):
        return Plan(intent="count_my_videos", confidence=0.95)

    if _contains_any(q, ["最早上传", "第一个上传", "最先上传"]):
        return Plan(intent="first_uploaded_video", confidence=0.95)

    if _contains_any(q, ["最近上传", "最新上传", "最后上传"]):
        return Plan(intent="latest_uploaded_video", confidence=0.92)

    if _contains_any(q, ["观看历史", "播放历史", "看过"]):
        return Plan(intent="watch_history", confidence=0.88)

    if _contains_any(q, ["未读通知", "通知有几", "几条通知"]):
        return Plan(intent="unread_notification_count", confidence=0.9)

    if "通知" in q:
        return Plan(intent="list_notifications", confidence=0.78)

    if "播放列表" in q and not _contains_any(q, ["创建", "新建", "加入", "添加", "删除"]):
        return Plan(intent="list_playlists", confidence=0.86)

    if _contains_any(q, ["评论数", "几条评论", "多少评论", "评论有多少"]):
        return Plan(intent="comment_count", args={"keyword": _extract_video_keyword(q)}, confidence=0.86)

    if _contains_any(q, ["评论"]) and _contains_any(q, ["看", "列", "有哪些", "查看"]):
        return Plan(intent="list_comments", args={"keyword": _extract_video_keyword(q)}, confidence=0.78)

    if _contains_any(q, ["点赞状态", "我点赞", "是否点赞"]):
        return Plan(intent="like_status", args={"keyword": _extract_video_keyword(q)}, confidence=0.8)

    if _contains_any(q, ["分享统计", "分享链接统计", "短链统计", "点击量"]):
        return Plan(intent="share_stats", args={"short_code": _extract_short_code(q)}, confidence=0.82)

    if _contains_any(q, ["分享链接", "生成分享", "创建分享"]):
        return Plan(
            intent="create_share",
            args={"keyword": _extract_video_keyword(q)},
            confidence=0.74,
            needs_confirmation=True,
        )

    if _contains_any(q, ["搜索", "找公开视频", "找一下", "公开视频"]):
        return Plan(
            intent="search_public_videos",
            args={"keyword": _extract_search_keyword(q), "category": _extract_category(q)},
            confidence=0.75,
        )

    if _contains_any(q, ["播放量", "观看量", "播放次数", "看了多少次"]):
        return Plan(
            intent="video_watch_count",
            args={"keyword": _extract_video_keyword(q)},
            confidence=0.86,
        )

    if _contains_any(q, ["状态", "处理到哪", "转码"]):
        return Plan(
            intent="video_status",
            args={"keyword": _extract_video_keyword(q)},
            confidence=0.82,
        )

    if _contains_any(q, ["流量", "消耗"]):
        return Plan(
            intent="video_traffic",
            args={"keyword": _extract_video_keyword(q)},
            confidence=0.76,
        )

    keyword = _extract_video_keyword(q)
    return Plan(intent="list_my_videos", args={"keyword": keyword}, confidence=0.5)


def plan_from_json(text: str) -> Plan:
    data = json.loads(text)
    return Plan(
        intent=str(data.get("intent", "list_my_videos")),
        args=data.get("args") or {},
        confidence=float(data.get("confidence", 0.0)),
        needs_confirmation=bool(data.get("needs_confirmation", False)),
        source="llm",
    )


def _contains_any(text: str, needles: list[str]) -> bool:
    return any(needle in text for needle in needles)


def _is_upload(text: str) -> bool:
    return "上传" in text and any(ext in text.lower() for ext in [".mp4", ".mov", ".mkv", ".avi", ".webm", ".mp3"])


def _extract_upload_args(text: str) -> dict[str, Any]:
    path_match = re.search(r"((?:~|\.{1,2}|/)[^\s，,。]+?\.(?:mp4|mov|mkv|avi|webm|mp3))", text, re.I)
    if not path_match:
        path_match = re.search(r"([\w\-\u4e00-\u9fff]+?\.(?:mp4|mov|mkv|avi|webm|mp3))", text, re.I)
    visibility = "PRIVATE" if "私密" in text or "私人" in text else "PUBLIC" if "公开" in text else "UNLISTED" if "不列出" in text else None
    title = None
    title_match = re.search(r"(?:标题|名字|命名为|叫)[：: ]*([^，,。]+)", text)
    if title_match:
        title = title_match.group(1).strip()
    return {
        "file_path": path_match.group(1).strip() if path_match else None,
        "title": title,
        "visibility": visibility,
    }


def _extract_video_keyword(text: str) -> str | None:
    bracket = re.search(r"[《\"']([^》\"']+)[》\"']", text)
    if bracket:
        return bracket.group(1).strip()

    cleaned = text
    for word in [
        "播放量",
        "观看量",
        "播放次数",
        "是多少",
        "多少",
        "状态",
        "流量",
        "消耗",
        "评论数",
        "评论",
        "点赞状态",
        "我点赞了吗",
        "生成分享链接",
        "分享链接",
        "这个视频",
        "某个视频",
        "视频",
        "我",
        "的",
        "？",
        "?",
    ]:
        cleaned = cleaned.replace(word, " ")
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    return cleaned or None


def _extract_search_keyword(text: str) -> str | None:
    cleaned = text
    cleaned = re.sub(r"分类[为是：: ]*[^\s，,。？?]+", " ", cleaned)
    for word in ["搜索", "找公开视频", "找一下", "公开视频", "视频", "分类", "有哪些", "？", "?"]:
        cleaned = cleaned.replace(word, " ")
    category = _extract_category(text)
    if category:
        cleaned = cleaned.replace(category, " ")
    cleaned = re.sub(r"[的为是]+", " ", cleaned)
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    return cleaned or None


def _extract_category(text: str) -> str | None:
    match = re.search(r"分类[为是：: ]*([^\s的，,。？?]+)", text)
    return match.group(1).strip() if match else None


def _extract_short_code(text: str) -> str | None:
    match = re.search(r"(?:shortCode|短链|短码|分享码|code)[：: ]*([A-Za-z0-9_-]+)", text)
    if match:
        return match.group(1)
    bare = re.search(r"\b([A-Za-z0-9_-]{4,16})\b", text)
    return bare.group(1) if bare else None
