"""Opt-in local-cover HTTP; ordinary registration shares the existing identity/journal cycle."""

from dataclasses import dataclass

from video_serverless.business.local_covers import LocalCoverError, LocalCovers
from video_serverless.http import Response
from video_serverless.playback_json import _spring_object
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.profile_json import _string
from video_serverless.request_cycle import CycleResult, RequestCycle
from video_serverless.user_application import UserBindingError, UserRequestError, _parameter

LOCAL_COVER_METHODS = {"/cover/local/createCover": "POST", "/cover/local/finishCover": "POST"}
_ERRORS = {14: "用户不存在", 22: "视频不存在", 23: "视频与上传者不匹配",
           34: "生成对象存储上传凭证失败", 41: "封面不存在"}


@dataclass(frozen=True)
class LocalCoverApplication:
    covers: LocalCovers
    cycle: RequestCycle

    def dispatch(self, request: RequestMetadata, body: bytes = b"") -> CycleResult:
        if request.path not in LOCAL_COVER_METHODS:
            return CycleResult(Response.json({"message": "Local cover route not found"}, 404), "not_started")
        if request.method != "POST":
            return CycleResult(self.cycle.error_response(UserBindingError("Request method is not supported")),
                               "not_started")

        def handle(metadata, identity):
            data = None
            try:
                if metadata.path.endswith("/createCover"):
                    fields = {}
                    for name, value in _spring_object(body, metadata.first_header("Content-Type")):
                        if name in {"videoId", "extension"}:
                            fields[name] = _string(value)
                    result = self.covers.create(identity.user_id, fields.get("videoId"), fields.get("extension"))
                    credentials = result.credentials
                    data = {"coverId": result.id, "coverKey": result.key, "uploadCredentials": {
                        "bucket": credentials.bucket, "key": credentials.key, "endpoint": credentials.endpoint,
                        "accessKeyId": credentials.access_key_id, "secretKey": credentials.secret_key,
                        "sessionToken": credentials.session_token, "expiration": credentials.expiration}}
                else:
                    self.covers.finish(identity.user_id, _parameter(metadata, "coverId"))
            except LocalCoverError as error:
                raise UserRequestError(error.code, _ERRORS[error.code], error.detail) from None
            response = Response.json({"code": 0, "message": "success", "data": data})
            return Response(response.status, response.body, {"Content-Type": "application/json"})

        return self.cycle.run(request, handle, protected=True)
