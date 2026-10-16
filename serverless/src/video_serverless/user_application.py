"""Explicit user HTTP application; not installed by either existing prototype entrypoint."""

from dataclasses import dataclass

from video_serverless.business.avatars import Avatars
from video_serverless.business.login import Login
from video_serverless.business.profile_updates import ProfileUpdates
from video_serverless.business.profiles import Profiles
from video_serverless.business.users import Users
from video_serverless.business.verification_codes import CodeRequests
from video_serverless.business.watching_ids import WatchingIds
from video_serverless.http import Response
from video_serverless.ports.profile_updates import ProfileUpdateResult
from video_serverless.ports.request_identity import RequestMetadata, ResolvedIdentity
from video_serverless.profile_json import decode_profile
from video_serverless.request_cycle import CycleResult, RequestCycle
from video_serverless.user_responses import (
    avatar_response,
    channel_response,
    client_id_response,
    login_response,
    profile_update_response,
    public_user_response,
    session_id_response,
    token_user_response,
)

_METHODS = {
    "/user/requestVerificationCode": "GET", "/user/submitVerificationCode": "GET",
    "/user/getUserByToken": "GET", "/user/getUserById": "GET",
    "/user/getMyProfile": "GET", "/user/getChannel": "GET", "/user/updateProfile": "POST",
    "/client/requestClientId": "GET", "/session/requestSessionId": "GET",
    "/user/createAvatarFile": "GET", "/user/getAvatarUploadCredentials": "GET",
    "/user/avatarUploadFinish": "GET",
}
_PROTECTED = {"/user/getMyProfile", "/user/updateProfile"}
_AVATAR_PATHS = {"/user/createAvatarFile", "/user/getAvatarUploadCredentials", "/user/avatarUploadFinish"}


class UserBindingError(ValueError):
    pass


class UserRequestError(RuntimeError):
    def __init__(self, code: int, message: str, detail: str | None = None):
        super().__init__(*(() if detail is None else (detail,)))
        self.code = code
        self.message = message
        self.detail = detail


def _parameter(request: RequestMetadata, name: str) -> str:
    value = request.first_parameter(name)
    if value is None:
        raise UserBindingError("Required request parameter is missing")
    return value


@dataclass(frozen=True)
class UserApplication:
    codes: CodeRequests
    login: Login
    users: Users
    profiles: Profiles
    updates: ProfileUpdates
    watching: WatchingIds
    cycle: RequestCycle
    avatars: Avatars | None = None

    def dispatch(self, request: RequestMetadata, body: bytes = b"") -> CycleResult:
        if request.path not in _METHODS or (request.path in _AVATAR_PATHS and self.avatars is None):
            return CycleResult(Response.json({"message": "User route not found"}, 404), "not_started")

        def handle(metadata: RequestMetadata, identity: ResolvedIdentity) -> Response:
            method = _METHODS[metadata.path]
            if metadata.method != method and not (metadata.method == "HEAD" and method == "GET"):
                raise UserBindingError("Request method is not supported")
            path = metadata.path
            if path == "/user/requestVerificationCode":
                self.codes.request(_parameter(metadata, "phone"))
                return profile_update_response(ProfileUpdateResult())
            if path == "/user/submitVerificationCode":
                result = self.login.submit(_parameter(metadata, "phone"), _parameter(metadata, "code"))
                if result.error_code is not None:
                    raise UserRequestError(result.error_code, {11: "验证码错误", 12: "验证码已过期"}[result.error_code])
                return login_response(result)
            if path == "/user/getUserByToken":
                return token_user_response(self.users.by_token(_parameter(metadata, "token")))
            if path == "/user/getUserById":
                return public_user_response(self.users.public_by_id(_parameter(metadata, "userId")))
            if path == "/user/getMyProfile":
                return public_user_response(self.profiles.mine(identity.user_id))
            if path == "/user/getChannel":
                return channel_response(self.profiles.channel(_parameter(metadata, "userId")))
            if path == "/user/updateProfile":
                mime = (metadata.first_header("Content-Type") or "").split(";", 1)[0].strip().lower()
                if mime != "application/json" and not (mime.startswith("application/") and mime.endswith("+json")):
                    raise UserBindingError("Profile JSON content type is required")
                value = decode_profile(body, metadata.first_header("Content-Type"))
                return profile_update_response(self.updates.update(identity.user_id, value.nickname, value.bio))
            if path == "/client/requestClientId":
                value = self.watching.client(metadata.remote_addr, metadata.first_header("User-Agent"))
                return client_id_response(value)
            if path in _AVATAR_PATHS:
                if path == "/user/createAvatarFile":
                    result = self.avatars.create(identity.user_id)
                elif path == "/user/getAvatarUploadCredentials":
                    result = self.avatars.credentials(identity.user_id, _parameter(metadata, "fileId"))
                else:
                    result = self.avatars.finish(identity.user_id, _parameter(metadata, "fileId"))
                if result.error_code is not None:
                    messages = {31: "文件不存在", 33: "文件和用户不匹配", 34: "生成对象存储上传凭证失败"}
                    raise UserRequestError(result.error_code, messages[result.error_code])
                return avatar_response(result)
            return session_id_response(self.watching.session(metadata.remote_addr, metadata.first_header("User-Agent")))

        return self.cycle.run(request, handle, protected=request.path in _PROTECTED)

