"""Existing notification BSON, Spring ID mapping and owner-scoped read-state updates."""

from datetime import UTC

from bson.codec_options import CodecOptions

from video_serverless.adapters.mongo_users import _date, _text
from video_serverless.adapters.mongo_video_status import _id

_TEXT = ("fromUserId", "toUserId", "type", "content", "relatedVideoId", "relatedCommentId")


def _read_flag(value):
    if value is None:
        raise ValueError("Explicit null cannot be mapped to the legacy primitive notification read flag")
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        normalized = value.strip().lower()
        if normalized in {"true", "on", "yes", "1"}:
            return True
        if normalized in {"false", "off", "no", "0"}:
            return False
    raise ValueError("Notification boolean mapping is not verified for this BSON value")


class MongoNotifications:
    def __init__(self, database_provider):
        self._database_provider = database_provider

    def _collection(self):
        return self._database_provider().get_collection(
            "notification", codec_options=CodecOptions(tz_aware=True, tzinfo=UTC))

    def page(self, user_id, skip, limit):
        cursor = self._collection().find({"toUserId": user_id}).sort(
            "createTime", -1).skip(max(skip, 0)).limit(max(limit, 0))
        with cursor:
            return tuple(self._map(row) for row in cursor)

    def count(self, user_id):
        return self._collection().count_documents({"toUserId": user_id})

    def count_unread(self, user_id):
        return self._collection().count_documents({"toUserId": user_id, "read": False})

    def mark_read(self, user_id, notification_id):
        self._collection().update_one({"_id": _id(notification_id), "toUserId": user_id}, {"$set": {"read": True}})

    def mark_all_read(self, user_id):
        self._collection().update_many({"toUserId": user_id, "read": False}, {"$set": {"read": True}})

    def _map(self, row):
        result = {name: _text(row.get(name)) for name in _TEXT}
        return {"id": _text(row["_id"]), **result, "read": _read_flag(row["read"]) if "read" in row else False,
                "createTime": _date(row.get("createTime"))}
