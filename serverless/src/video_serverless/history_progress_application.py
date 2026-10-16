"""Explicit history/progress HTTP capability; not installed in existing entrypoints."""

from dataclasses import dataclass

from video_serverless.business.watch_history import WatchHistory
from video_serverless.http import Response
from video_serverless.ports.progress import ProgressStore
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.public_video_application import _integer
from video_serverless.request_cycle import CycleResult, RequestCycle
from video_serverless.user_responses import _date
from video_serverless.video_responses import video_result

HISTORY_PROGRESS_ROUTES = {"/progress/getProgress", "/watchHistory/getMyHistory", "/watchHistory/clear"}


@dataclass(frozen=True)
class HistoryProgressApplication:
    progress: ProgressStore
    history: WatchHistory
    cycle: RequestCycle

    def dispatch(self, request: RequestMetadata, body: bytes = b"") -> CycleResult:
        if request.path not in HISTORY_PROGRESS_ROUTES:
            return CycleResult(Response.json({"message": "History/progress route not found"}, 404), "not_started")

        def handle(metadata, identity):
            method = "DELETE" if metadata.path == "/watchHistory/clear" else "GET"
            if metadata.method != method and not (metadata.method == "HEAD" and method == "GET"):
                raise ValueError("Request method is not supported")
            if metadata.path == "/progress/getProgress":
                record = self.progress.find(metadata.first_parameter("videoId"), identity.user_id,
                                            metadata.first_parameter("clientId"))
                data = None if record is None else {
                    "id": record.id, "videoId": record.video_id, "viewerId": record.viewer_id,
                    "clientId": record.client_id, "sessionId": record.session_id,
                    "progressInMillis": record.milliseconds, "createTime": _date(record.created_at),
                    "updateTime": _date(record.updated_at),
                }
                return video_result(data)
            if metadata.path == "/watchHistory/clear":
                self.history.clear(identity.user_id)
                return video_result(None)
            page = self.history.get(identity.user_id, _integer(metadata, "page", 0), _integer(metadata, "pageSize", 20))
            return video_result({"list": [{"videoId": item.video_id, "title": item.title,
                                           "coverUrl": item.cover_url, "watchTime": _date(item.watched_at)}
                                          for item in page.items],
                                 "total": page.total, "page": page.page, "pageSize": page.page_size})

        # The legacy history/progress paths have PutToken but are not registered in CheckToken.
        return self.cycle.run(request, handle)
