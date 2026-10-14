"""Legacy user payload encoding into portable HTTP values; no routes or adapters are installed."""

from datetime import UTC, datetime

from video_serverless.http import Response
from video_serverless.ports.users import UserSnapshot


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
    response = Response.json({"code": code, "message": message, "data": _user(user)})
    # Spring's existing JSON content type does not add a charset parameter.
    return Response(response.status, response.body, {"Content-Type": "application/json"})


def token_user_response(user: UserSnapshot | None) -> Response:
    if user is None:
        return _result(None, 13, "登陆token校验未通过")
    return _result(user)


def public_user_response(user: UserSnapshot | None) -> Response:
    return _result(user)
