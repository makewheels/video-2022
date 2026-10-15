"""Legacy viewer equality and bounded reads, plus single deleteMany without application replay."""

from datetime import UTC

from bson import ObjectId
from bson.codec_options import CodecOptions

from video_serverless.adapters.mongo_users import _date, _text
from video_serverless.adapters.mongo_watch_info import MongoWatchReads
from video_serverless.ports.watch_history import HistoryRow, HistoryVideo


class MongoHistoryStore:
    def __init__(self, database_provider):
        self._database_provider = database_provider

    def _logs(self):
        return self._database_provider().get_collection(
            "watchLog", codec_options=CodecOptions(tz_aware=True, tzinfo=UTC)
        )

    def page(self, viewer_id, skip, limit):
        cursor = self._logs().find({"viewerId": viewer_id}, {"videoId": 1, "createTime": 1}, max_time_ms=1_000)
        with cursor.sort("createTime", -1).skip(max(skip, 0)).limit(max(limit, 0)):
            return tuple(HistoryRow(_text(doc.get("videoId")), _date(doc.get("createTime"))) for doc in cursor)

    def count(self, viewer_id):
        return self._logs().count_documents({"viewerId": viewer_id}, maxTimeMS=1_000)

    def clear(self, viewer_id):
        self._logs().delete_many({"viewerId": viewer_id})

    def videos(self, ids):
        keys = [ObjectId(value) if ObjectId.is_valid(value) else value for value in ids]
        cursor = self._database_provider().get_collection("video").find(
            {"_id": {"$in": keys}}, {"title": 1, "coverId": 1}, max_time_ms=1_000
        )
        with cursor:
            return tuple(HistoryVideo(_text(doc["_id"]), _text(doc.get("title")), _text(doc.get("coverId")))
                         for doc in cursor)


class MongoHistoryCoverUrls:
    def __init__(self, database_provider, urls):
        self._reads = MongoWatchReads(database_provider)
        self._urls = urls

    def signed(self, cover_id):
        key = self._reads.cover_key(cover_id)
        return None if key is None else self._urls.sign(key, 7200)
