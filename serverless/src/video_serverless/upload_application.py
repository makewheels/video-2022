"""Explicit ordinary creation HTTP capability, sharing the existing request lifecycle."""

from dataclasses import dataclass

from video_serverless.business.video_creation import CreationArgumentError, VideoCreation
from video_serverless.http import Response
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.request_cycle import CycleResult, RequestCycle
from video_serverless.upload_json import decode_creation
from video_serverless.user_application import UserBindingError, UserRequestError
from video_serverless.video_status_application import VIDEO_STATUS_ROUTE, VideoStatusApplication


@dataclass(frozen=True)
class UploadApplication:
    creation: VideoCreation
    cycle: RequestCycle
    statuses: VideoStatusApplication | None = None

    def dispatch(self, request: RequestMetadata, body: bytes = b"") -> CycleResult:
        if self.statuses is not None and request.path == VIDEO_STATUS_ROUTE:
            return self.statuses.dispatch(request, body)
        if request.path != "/video/create":
            return CycleResult(Response.json({"message": "Upload route not found"}, 404), "not_started")

        def handle(metadata, identity):
            if metadata.method != "POST":
                raise UserBindingError("Request method is not supported")
            dto = decode_creation(body, metadata.first_header("Content-Type"))
            try:
                result = self.creation.create(identity.user_id, dto)
            except CreationArgumentError as error:
                raise UserRequestError(21, "视频创建参数不合法", str(error)) from None
            response = Response.json({"code": 0, "message": "video created", "data": {
                "videoId": result.video_id, "fileId": result.file_id, "watchId": result.watch_id,
                "watchUrl": result.watch_url, "shortUrl": result.watch_url,
            }})
            return Response(response.status, response.body, {"Content-Type": "application/json"})

        return self.cycle.run(request, handle, protected=True)
