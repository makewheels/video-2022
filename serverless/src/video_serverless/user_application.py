"""Explicit user HTTP application; not installed by either existing prototype entrypoint."""

from dataclasses import dataclass

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
}
_PROTECTED = {"/user/getMyProfile", "/user/updateProfile"}


class UserBindingError(ValueError):
    pass


class UserRequestError(RuntimeError):
    def __init__(self, code: int, message: str):
        super().__init__()
        self.code = code
        self.message = message


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

    def dispatch(self, request: RequestMetadata, body: bytes = b"") -> CycleResult:
        if request.path not in _METHODS:
            return CycleResult(Response.json({"message": "User route not found"}, 404), "not_started")

        def handle(metadata: RequestMetadata, identity: ResolvedIdentity) -> Response:
            if metadata.method != _METHODS[metadata.path]:
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
                value = decode_profile(body)
                return profile_update_response(self.updates.update(identity.user_id, value.nickname, value.bio))
            if path == "/client/requestClientId":
                value = self.watching.client(metadata.remote_addr, metadata.first_header("User-Agent"))
                return client_id_response(value)
            return session_id_response(self.watching.session(metadata.remote_addr, metadata.first_header("User-Agent")))

        return self.cycle.run(request, handle, protected=request.path in _PROTECTED)

