"""Explicit legacy watch-log HTTP capability; production entrypoints remain separate."""

from dataclasses import dataclass

from video_serverless.business.watch_log import WatchLogs
from video_serverless.http import Response
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.ports.watch_log import WatchVisit
from video_serverless.request_cycle import CycleResult, RequestCycle
from video_serverless.user_application import UserBindingError, UserRequestError

WATCH_LOG_ROUTE = "/watchController/addWatchLog"


@dataclass(frozen=True)
class WatchLogApplication:
    logs: WatchLogs
    cycle: RequestCycle

    def dispatch(self, request: RequestMetadata, body: bytes = b"") -> CycleResult:
        if request.path != WATCH_LOG_ROUTE:
            return CycleResult(Response.json({"message": "Watch log route not found"}, 404), "not_started")

        def handle(metadata, identity):
            status = metadata.first_parameter("videoStatus")
            if metadata.method != "GET" or status is None:
                raise UserBindingError("Watch log request binding failed")
            # RequestUtil context reads query parameters; headers are not fallbacks.
            visit = WatchVisit(metadata.first_parameter("videoId"), metadata.first_parameter("clientId"),
                               metadata.first_parameter("sessionId"), identity.user_id,
                               metadata.remote_addr, metadata.first_header("User-Agent"))
            if not self.logs.store.video_exists(visit.video_id):
                video_id = "null" if visit.video_id is None else visit.video_id
                raise UserRequestError(22, "视频不存在", "视频不存在, videoId = " + video_id)
            code = self.logs.record(visit, status)
            response = Response.json({"code": code, "message": "视频不存在" if code == 22 else "success",
                                      "data": None})
            return Response(200, response.body, {"Content-Type": "application/json"})

        # PutToken applies, but this legacy path has no CheckToken gate.
        return self.cycle.run(request, handle)
