"""Shared legacy VideoVO encoding for public listing and search."""

from datetime import UTC

from video_serverless.http import Response


def _date(value):
    return None if value is None else value.astimezone(UTC).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def video_rows(cards):
    rows = []
    for card in cards:
        video = card.source
        rows.append({
            "id": video.id, "userId": None, "watchCount": video.watch_count, "duration": video.duration,
            "coverUrl": card.cover_url, "watchId": video.watch_id, "watchUrl": video.watch_url,
            "shortUrl": video.short_url, "title": video.title, "description": video.description,
            "type": None, "status": video.status, "visibility": video.visibility,
            "tags": None if video.tags is None else list(video.tags), "category": video.category,
            "uploaderName": card.uploader_name, "uploaderAvatarUrl": card.uploader_avatar_url,
            "uploaderId": card.uploader_id, "createTime": _date(video.create_time),
            "createTimeString": card.create_time_string, "youtubePublishTime": None,
            "youtubePublishTimeString": card.youtube_publish_time_string,
        })
    return rows


def video_result(data):
    response = Response.json({"code": 0, "message": "success", "data": data})
    return Response(response.status, response.body, {"Content-Type": "application/json"})
