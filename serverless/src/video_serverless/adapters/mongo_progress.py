"""Legacy progress replacement using the existing lazy, bounded Mongo provider."""

from datetime import UTC, datetime

from bson import ObjectId
from bson.codec_options import CodecOptions
from bson.int64 import Int64

from video_serverless.adapters.mongo_users import _date, _long, _text
from video_serverless.ports.progress import ProgressRecord


class MongoProgressStore:
    def __init__(self, database_provider, clock=None):
        self._database_provider = database_provider
        self._clock = clock if clock is not None else lambda: datetime.now(UTC)

    def _collection(self):
        return self._database_provider().get_collection(
            "progress", codec_options=CodecOptions(tz_aware=True, tzinfo=UTC)
        )

    def find(self, video_id, viewer_id, client_id):
        if viewer_id is None and client_id is None:
            raise ValueError("viewerId和clientId不能同时为空")
        query = {"videoId": video_id}
        query["viewerId" if viewer_id is not None else "clientId"] = viewer_id if viewer_id is not None else client_id
        document = self._collection().find_one(query, max_time_ms=1_000)
        if document is None:
            return None
        return ProgressRecord(_text(document["_id"]), _text(document.get("videoId")),
                              _text(document.get("viewerId")), _text(document.get("clientId")),
                              _text(document.get("sessionId")), _long(document.get("progressInMillis")),
                              _date(document["createTime"] if "createTime" in document else self._clock()),
                              _date(document["updateTime"] if "updateTime" in document else self._clock()))

    def save(self, progress):
        fields = {"videoId": progress.video_id, "viewerId": progress.viewer_id, "clientId": progress.client_id,
                  "sessionId": progress.session_id, "createTime": progress.created_at,
                  "updateTime": progress.updated_at}
        document = {key: value for key, value in fields.items() if value is not None}
        if progress.milliseconds is not None:
            document["progressInMillis"] = Int64(progress.milliseconds)
        collection = self._collection()
        if progress.id is None:
            collection.insert_one(document)
        else:
            key = ObjectId(progress.id) if ObjectId.is_valid(progress.id) else progress.id
            document["_id"] = key
            # MongoTemplate.save replaces the mapped entity and can recreate a removed record.
            collection.replace_one({"_id": key}, document, upsert=True)
