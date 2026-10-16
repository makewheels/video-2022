"""Explicit LOCAL creation HTTP; completion stays absent until object scanning is migrated."""

from dataclasses import dataclass

from video_serverless.business.local_covers import LocalCoverError
from video_serverless.business.local_transcodes import LocalTranscodes
from video_serverless.heartbeat_json import _decimal
from video_serverless.http import Response
from video_serverless.java_numbers import double_text
from video_serverless.local_cover_application import _ERRORS
from video_serverless.playback_json import _spring_object
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.request_cycle import CycleResult, RequestCycle
from video_serverless.upload_json import _fields, _validate
from video_serverless.user_application import UserBindingError, UserRequestError

LOCAL_TRANSCODE_METHODS = {"/transcode/local/createTranscode": "POST"}
_SCHEMA = _fields("videoId resolution videoCodec audioCodec frameRateFraction frameRateMode sar pixFmt "
                  "colorPrimaries colorTransfer colorSpace colorRange dynamicRange", "durationMs",
                  "width height bitrate displayWidth displayHeight rotation bitDepth audioTrackCount")


def _double_text(value):
    number = _decimal(value)
    return None if number is None else double_text(float(number))


def decode_local_transcode(body, content_type):
    fields = {}
    for name, value in _spring_object(body, content_type):
        if name == "frameRate":
            fields["frameRateText"] = _double_text(value)
        elif name in _SCHEMA:
            fields[name] = _validate(value, _SCHEMA[name])
    return fields


@dataclass(frozen=True)
class LocalTranscodeApplication:
    transcodes: LocalTranscodes
    cycle: RequestCycle

    def dispatch(self, request: RequestMetadata, body: bytes = b"") -> CycleResult:
        if request.path not in LOCAL_TRANSCODE_METHODS:
            return CycleResult(Response.json({"message": "Local transcode route not found"}, 404), "not_started")
        if request.method != "POST":
            return CycleResult(self.cycle.error_response(UserBindingError("Request method is not supported")),
                               "not_started")

        def handle(metadata, identity):
            try:
                fields = decode_local_transcode(body, metadata.first_header("Content-Type"))
                data = self.transcodes.create(identity.user_id, fields)
            except LocalCoverError as error:
                messages = _ERRORS | {21: "视频创建参数不合法"}
                raise UserRequestError(error.code, messages[error.code], error.detail) from None
            credentials = data["uploadCredentials"]
            data["uploadCredentials"] = {
                "bucket": credentials.bucket, "key": credentials.key, "endpoint": credentials.endpoint,
                "accessKeyId": credentials.access_key_id, "secretKey": credentials.secret_key,
                "sessionToken": credentials.session_token, "expiration": credentials.expiration}
            response = Response.json({"code": 0, "message": "success", "data": data})
            return Response(response.status, response.body, {"Content-Type": "application/json"})

        return self.cycle.run(request, handle, protected=True)
