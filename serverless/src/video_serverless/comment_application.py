"""Explicit public comment reads using the old parameter binding and synchronous request cycle."""

from dataclasses import dataclass
from datetime import UTC, datetime

from video_serverless.business.comment_reads import CommentReadError, Comments
from video_serverless.http import Response
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.public_video_application import _integer
from video_serverless.request_cycle import CycleResult, RequestCycle
from video_serverless.user_application import UserBindingError, UserRequestError, _parameter

COMMENT_READ_METHODS = dict.fromkeys(("/comment/getByVideoId", "/comment/getReplies", "/comment/getCount"), "GET")


def _row(comment):
    row = {}
    for name, value in comment.items():
        if isinstance(value, datetime):
            value = value.astimezone(UTC).isoformat(timespec="milliseconds").replace("+00:00", "Z")
        row[name] = value
    return row


@dataclass(frozen=True)
class CommentReadApplication:
    comments: Comments
    cycle: RequestCycle

    def dispatch(self, request: RequestMetadata, body: bytes = b"") -> CycleResult:
        if request.path not in COMMENT_READ_METHODS:
            return CycleResult(Response.json({"message": "Comment route not found"}, 404), "not_started")
        if request.method not in {"GET", "HEAD"}:
            return CycleResult(self.cycle.error_response(UserBindingError("Request method is not supported")),
                               "not_started")

        def handle(metadata, identity):
            try:
                if metadata.path == "/comment/getByVideoId":
                    video_id = _parameter(metadata, "videoId")
                    page = _integer(metadata, "page", 0)
                    size = _integer(metadata, "pageSize", 20)
                    sort_by = metadata.first_parameter("sortBy") or "createTime"
                    result = self.comments.page(video_id, page, size, sort_by)
                    data = {"list": [_row(row) for row in result.content], "total": result.total,
                            "totalPages": result.total_pages, "currentPage": result.current_page,
                            "pageSize": result.page_size}
                elif metadata.path == "/comment/getReplies":
                    result = self.comments.replies(_parameter(metadata, "parentId"),
                                                   _integer(metadata, "skip", 0), _integer(metadata, "limit", 20))
                    data = [_row(row) for row in result]
                else:
                    data = {"count": self.comments.count(_parameter(metadata, "videoId"))}
            except CommentReadError as error:
                raise UserRequestError(22, "视频不存在", str(error)) from None
            response = Response.json({"code": 0, "message": "success", "data": data})
            return Response(response.status, response.body, {"Content-Type": "application/json"})

        return self.cycle.run(request, handle)
