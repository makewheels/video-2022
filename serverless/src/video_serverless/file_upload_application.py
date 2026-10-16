"""Explicit file upload routes with existing protected request and journal behavior."""

from dataclasses import dataclass

from video_serverless.business.file_uploads import FileUploads, UploadFileError
from video_serverless.http import Response
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.request_cycle import CycleResult, RequestCycle
from video_serverless.user_application import UserBindingError, UserRequestError, _parameter

FILE_UPLOAD_ROUTES = frozenset({"/file/getUploadCredentials", "/file/uploadFinish"})
_ERRORS = {15: "用户未登录", 31: "文件不存在", 33: "文件和用户不匹配", 34: "生成对象存储上传凭证失败"}


@dataclass(frozen=True)
class FileUploadApplication:
    uploads: FileUploads
    cycle: RequestCycle

    def dispatch(self, request: RequestMetadata, body: bytes = b"") -> CycleResult:
        if request.path not in FILE_UPLOAD_ROUTES:
            return CycleResult(Response.json({"message": "File upload route not found"}, 404), "not_started")

        def handle(metadata, identity):
            if metadata.method not in {"GET", "HEAD"}:
                raise UserBindingError("Request method is not supported")
            file_id = _parameter(metadata, "fileId")
            data = None
            try:
                if metadata.path == "/file/getUploadCredentials":
                    result = self.uploads.credentials(identity.user_id, file_id)
                    credentials = result.credentials
                    data = {"bucket": credentials.bucket, "key": credentials.key, "endpoint": credentials.endpoint,
                            "accessKeyId": credentials.access_key_id, "secretKey": credentials.secret_key,
                            "sessionToken": credentials.session_token, "expiration": credentials.expiration,
                            "provider": result.provider}
                else:
                    self.uploads.finish(identity.user_id, file_id)
            except UploadFileError as error:
                raise UserRequestError(error.code, _ERRORS[error.code], error.detail) from None
            response = Response.json({"code": 0, "message": "success", "data": data})
            return Response(response.status, response.body, {"Content-Type": "application/json"})

        return self.cycle.run(request, handle, protected=True)
