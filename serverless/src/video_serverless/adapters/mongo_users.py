"""Read-only legacy user mapping; deliberately not wired into the HTTP application yet."""

import math
import re
from datetime import UTC, datetime

from bson import ObjectId
from bson.codec_options import CodecOptions

from video_serverless.ports.users import UserSnapshot


class UserMappingError(ValueError):
    """Unsupported BSON shape, without record values or credentials in the message."""


def _text(value):
    if value is None or isinstance(value, str):
        return value
    if isinstance(value, (ObjectId, int)) and not isinstance(value, bool):
        return str(value)
    raise UserMappingError("User text conversion is not verified for this BSON type")


def _long(value):
    if value is None:
        return None
    if isinstance(value, bool):
        raise UserMappingError("User counter conversion is not verified for this BSON type")
    if isinstance(value, int):
        result = int(value)
    elif isinstance(value, float) and math.isfinite(value):
        result = int(value)
    elif isinstance(value, str) and re.fullmatch(r"[+-]?[0-9]+", value):
        result = int(value)
    else:
        raise UserMappingError("User counter conversion is not verified for this BSON type")
    if not -(2**63) <= result < 2**63:
        raise UserMappingError("User counter conversion is not verified outside the signed long range")
    return result


def _date(value):
    if value is None:
        return None
    if not isinstance(value, datetime) or value.tzinfo is None or value.utcoffset() is None:
        raise UserMappingError("User date conversion requires a BSON date with explicit UTC decoding")
    return value.astimezone(UTC)


class MongoUserReads:
    def __init__(self, database_provider, clock=None):
        # Reuse the composition root's existing lazy, bounded connection pool.
        self._database_provider = database_provider
        self._clock = clock if clock is not None else lambda: datetime.now(UTC)

    def find_by_token(self, token: str) -> UserSnapshot | None:
        # Exact BSON string equality: no trimming, Bearer parsing or numeric coercion.
        return self._read({"token": token})

    def find_by_id(self, user_id: str) -> UserSnapshot | None:
        key = ObjectId(user_id) if ObjectId.is_valid(user_id) else user_id
        # Spring converts a valid hex String @Id to ObjectId, without String fallback.
        return self._read({"_id": key})

    def _read(self, query):
        collection = self._database_provider().get_collection(
            "user", codec_options=CodecOptions(tz_aware=True, tzinfo=UTC)
        )
        document = collection.find_one(query, max_time_ms=1_000)
        if document is None:
            return None
        fields = {
            "id": _text(document["_id"]),
            "create_time": _date(document["createTime"] if "createTime" in document else self._clock()),
            "update_time": _date(document["updateTime"] if "updateTime" in document else self._clock()),
            "subscriber_count": _long(document.get("subscriberCount", 0)),
            "video_count": _long(document.get("videoCount", 0)),
        }
        for target, source in (
            ("phone", "phone"), ("register_channel", "registerChannel"), ("token", "token"),
            ("nickname", "nickname"), ("avatar_file_id", "avatarFileId"),
            ("avatar_url", "avatarUrl"), ("banner_url", "bannerUrl"), ("bio", "bio"),
        ):
            fields[target] = _text(document.get(source))
        return UserSnapshot(**fields)
