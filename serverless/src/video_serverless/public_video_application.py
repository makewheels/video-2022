"""Explicit public-list HTTP capability, sharing the established request lifecycle."""

import re
from dataclasses import dataclass

from video_serverless.business.public_videos import PublicVideos
from video_serverless.http import Response
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.request_cycle import CycleResult, RequestCycle
from video_serverless.video_responses import video_result, video_rows


def _integer(request, name, default=None):
    value = request.first_parameter(name)
    if default is not None and (value is None or value == ""):
        return default
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


@dataclass(frozen=True)
class PublicVideoApplication:
    videos: PublicVideos
    cycle: RequestCycle

    def dispatch(self, request: RequestMetadata, body: bytes = b"") -> CycleResult:
        if request.path != "/video/getPublicVideoList":
            return CycleResult(Response.json({"message": "Video route not found"}, 404), "not_started")

        def handle(metadata, identity):
            if metadata.method not in {"GET", "HEAD"}:
                raise ValueError("Request method is not supported")
            page = self.videos.page(_integer(metadata, "skip"), _integer(metadata, "limit"),
                                    metadata.first_parameter("keyword"))
            return video_result({"list": video_rows(page.videos), "total": page.total})

        return self.cycle.run(request, handle)
