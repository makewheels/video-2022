"""Explicit public-list HTTP capability, sharing the established request lifecycle."""

import re
from dataclasses import dataclass
from datetime import UTC

from video_serverless.business.public_videos import PublicVideos
from video_serverless.http import Response
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.request_cycle import CycleResult, RequestCycle


def _integer(request, name):
    value = request.first_parameter(name)
    if value is None:
        raise ValueError("Required pagination parameter is missing")
    # Verified against Spring NumberUtils7.0.5, not FastAPI's default integer parsing.
    value = "".join(char for char in value if not (char.isspace() and char not in "\x85\u00a0\u2007\u202f"))
    negative = value.startswith("-")
    unsigned = value[1:] if negative else value
    if unsigned.startswith(("0x", "0X", "#")):
        digits = unsigned[1:] if unsigned.startswith("#") else unsigned[2:]
        if not re.fullmatch(r"[0-9a-fA-F]+", digits):
            raise ValueError("Pagination parameter is not an integer")
        number = int(digits, 16) * (-1 if negative else 1)
    else:
        if not re.fullmatch(r"[+-]?\d+", value):
            raise ValueError("Pagination parameter is not an integer")
        number = int(value)
    if not -(2**31) <= number < 2**31:
        raise ValueError("Pagination parameter is outside the Java int range")
    return number


def _date(value):
    return None if value is None else value.astimezone(UTC).isoformat(timespec="milliseconds").replace("+00:00", "Z")


@dataclass(frozen=True)
class PublicVideoApplication:
    videos: PublicVideos
    cycle: RequestCycle

    def dispatch(self, request: RequestMetadata, body: bytes = b"") -> CycleResult:
        if request.path != "/video/getPublicVideoList":
            return CycleResult(Response.json({"message": "Video route not found"}, 404), "not_started")

        def handle(metadata, identity):
            if metadata.method != "GET":
                raise ValueError("Request method is not supported")
            page = self.videos.page(_integer(metadata, "skip"), _integer(metadata, "limit"),
                                    metadata.first_parameter("keyword"))
            rows = []
            for card in page.videos:
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
            response = Response.json({"code": 0, "message": "success", "data": {"list": rows, "total": page.total}})
            return Response(response.status, response.body, {"Content-Type": "application/json"})

        return self.cycle.run(request, handle)
