"""Protected lightweight status polling, with the existing request journal and error mapping."""

from dataclasses import dataclass

from video_serverless.business.video_status import VideoStatusError, VideoStatuses
from video_serverless.http import Response
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.request_cycle import CycleResult, RequestCycle
from video_serverless.user_application import UserBindingError, UserRequestError, _parameter

VIDEO_STATUS_ROUTE = "/video/getVideoStatus"
_ERRORS = {14: "用户不存在", 22: "视频不存在", 23: "视频与上传者不匹配"}


@dataclass(frozen=True)
class VideoStatusApplication:
    statuses: VideoStatuses
    cycle: RequestCycle

    def dispatch(self, request: RequestMetadata, body: bytes = b"") -> CycleResult:
        if request.path != VIDEO_STATUS_ROUTE:
            return CycleResult(Response.json({"message": "Video status route not found"}, 404), "not_started")

        def handle(metadata, identity):
            if metadata.method not in {"GET", "HEAD"}:
                raise UserBindingError("Request method is not supported")
            try:
                result = self.statuses.get(identity.user_id, _parameter(metadata, "videoId"))
            except VideoStatusError as error:
                raise UserRequestError(error.code, _ERRORS[error.code], error.detail) from None
            response = Response.json({"code": 0, "message": "success", "data": {
                "videoId": result.id, "status": result.status, "isReady": result.status == "READY",
            }})
            return Response(response.status, response.body, {"Content-Type": "application/json"})

        return self.cycle.run(request, handle, protected=True)
