"""Legacy watchLog insert and Int64 video count increment, sharing the lazy provider."""

from decimal import Decimal

from bson import ObjectId
from bson.decimal128 import Decimal128
from bson.int64 import Int64

from video_serverless.adapters.mongo_users import _text
from video_serverless.ports.watch_log import WatchVideo


def _id(value):
    return ObjectId(value) if ObjectId.is_valid(value) else value


def _ip_document(value):
    if isinstance(value, dict):
        if any(not isinstance(key, str) or "." in key for key in value):
            raise ValueError("Legacy IP map key cannot be stored")
        return {key: _ip_document(item) for key, item in value.items()}
    if isinstance(value, list):
        return [_ip_document(item) for item in value]
    if isinstance(value, Decimal):
        return Decimal128(value)
    return value


class MongoWatchLogStore:
    def __init__(self, database_provider):
        self._database_provider = database_provider

    def video_exists(self, video_id):
        return self._database_provider().get_collection("video").find_one(
            {"_id": _id(video_id)}, {"_id": 1}, max_time_ms=1_000
        ) is not None

    def duplicate(self, video_id, session_id, requested_status):
        return self._database_provider().get_collection("watchLog").find_one(
            {"videoId": video_id, "sessionId": session_id, "videoStatus": requested_status},
            {"_id": 1}, max_time_ms=1_000
        ) is not None

    def video(self, video_id):
        record = self._database_provider().get_collection("video").find_one(
            {"_id": _id(video_id)}, {"title": 1, "status": 1}, max_time_ms=1_000
        )
        return None if record is None else WatchVideo(
            _text(record["_id"]), _text(record.get("title")), _text(record.get("status", "CREATED")))

    def increment(self, video_id):
        self._database_provider().get_collection("video").update_one(
            {"_id": _id(video_id)}, {"$inc": {"watch.watchCount": Int64(1)}}, upsert=False
        )

    def save(self, event):
        visit = event.visit
        fields = {"videoId": visit.video_id, "clientId": visit.client_id, "sessionId": visit.session_id,
                  "viewerId": visit.viewer_id, "ip": visit.ip, "userAgent": visit.user_agent,
                  "videoStatus": event.video_status, "createTime": event.created_at}
        document = {key: value for key, value in fields.items() if value is not None}
        if event.ip_info is not None:
            document["ipInfo"] = _ip_document(event.ip_info)
        self._database_provider().get_collection("watchLog").insert_one(document)
