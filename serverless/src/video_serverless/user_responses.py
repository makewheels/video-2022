"""Legacy user payload encoding into portable HTTP values; no routes or adapters are installed."""

from datetime import UTC, datetime

from video_serverless.http import Response
from video_serverless.ports.avatars import AvatarResult
from video_serverless.ports.login import LoginResult
from video_serverless.ports.profile_updates import ProfileUpdateResult
from video_serverless.ports.users import ChannelSnapshot, UserSnapshot


def _date(value: datetime | None) -> str | None:
    if value is None:
        return None
    if value.tzinfo is None or value.utcoffset() is None:
        raise ValueError("User snapshot timestamps must have an explicit timezone")
    return value.astimezone(UTC).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def _user(user: UserSnapshot | None):
    if user is None:
        return None
    return {
        "id": user.id,
        "phone": user.phone,
        "registerChannel": user.register_channel,
        "createTime": _date(user.create_time),
        "updateTime": _date(user.update_time),
        "token": user.token,
        "nickname": user.nickname,
        "avatarFileId": user.avatar_file_id,
        "avatarUrl": user.avatar_url,
        "bannerUrl": user.banner_url,
        "bio": user.bio,
        "subscriberCount": user.subscriber_count,
        "videoCount": user.video_count,
    }


def _result(user: UserSnapshot | None, code=0, message="success") -> Response:
    return _payload(_user(user), code, message)


def _payload(data, code=0, message="success") -> Response:
    response = Response.json({"code": code, "message": message, "data": data})
    # Spring's existing JSON content type does not add a charset parameter.
    return Response(response.status, response.body, {"Content-Type": "application/json"})


def token_user_response(user: UserSnapshot | None) -> Response:
    if user is None:
        return _result(None, 13, "登陆token校验未通过")
    return _result(user)


def public_user_response(user: UserSnapshot | None) -> Response:
    return _result(user)


def channel_response(channel: ChannelSnapshot | None) -> Response:
    if channel is None:
        return _payload(None, 1, "用户不存在")
    return _payload({
        "userId": channel.user_id, "nickname": channel.nickname, "avatarUrl": channel.avatar_url,
        "bannerUrl": channel.banner_url, "bio": channel.bio,
        "subscriberCount": channel.subscriber_count, "videoCount": channel.video_count,
        "isSubscribed": channel.is_subscribed,
    })


def profile_update_response(result: ProfileUpdateResult) -> Response:
    return _payload(None) if result.error is None else _payload(None, 1, result.error)


def login_response(result: LoginResult) -> Response:
    if result.error_code is not None:
        return _payload(None, result.error_code, {11: "验证码错误", 12: "验证码已过期"}[result.error_code])
    return _result(result.user)


def client_id_response(client_id: str) -> Response:
    return _payload({"clientId": client_id})


def session_id_response(session_id: str) -> Response:
    return _payload({"sessionId": session_id})


def avatar_response(result: AvatarResult) -> Response:
    if result.credentials is not None:
        credentials = result.credentials
        return _payload({"bucket": credentials.bucket, "key": credentials.key, "endpoint": credentials.endpoint,
                         "accessKeyId": credentials.access_key_id, "secretKey": credentials.secret_key,
                         "sessionToken": credentials.session_token, "expiration": credentials.expiration,
                         "fileId": result.file_id})
    if result.file_id is not None:
        return _payload({"fileId": result.file_id, "key": result.key})
    return _payload(None)
