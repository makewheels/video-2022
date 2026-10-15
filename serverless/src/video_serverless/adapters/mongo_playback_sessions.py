"""Legacy playbackSession mapping with BSON Long/Integer/Date and ID-specific replacement."""

from datetime import UTC

from bson import ObjectId
from bson.codec_options import CodecOptions
from bson.int64 import Int64

from video_serverless.adapters.mongo_users import _date, _long, _text
from video_serverless.ports.playback_sessions import PlaybackSession

TEXT = {"watch_id": "watchId", "video_id": "videoId", "user_id": "userId", "client_id": "clientId",
        "session_id": "sessionId", "resolution": "resolution", "exit_type": "exitType"}
DATES = {"started_at": "startTime", "ended_at": "endTime", "created_at": "createTime", "updated_at": "updateTime"}
LONGS = {"duration_ms": "totalPlayDurationMs", "max_progress_ms": "maxProgressMs",
         "current_progress_ms": "currentProgressMs"}


class MongoPlaybackSessionStore:
    def __init__(self, database_provider):
        self._database_provider = database_provider

    def _collection(self):
        return self._database_provider().get_collection(
            "playbackSession", codec_options=CodecOptions(tz_aware=True, tzinfo=UTC)
        )

    def find(self, session_id):
        if session_id is None:
            raise ValueError("Legacy playback session ID must not be null")
        key = ObjectId(session_id) if ObjectId.is_valid(session_id) else session_id
        document = self._collection().find_one({"_id": key}, max_time_ms=1_000)
        if document is None:
            return None
        fields = {target: _text(document.get(source)) for target, source in TEXT.items()}
        fields.update({target: _date(document.get(source)) for target, source in DATES.items()})
        fields.update({target: _long(document.get(source)) for target, source in LONGS.items()})
        counter = document.get("heartbeatCount")
        # Non-int32 persisted counter conversions require additional old BSON fixtures.
        if counter is not None and (type(counter) is not int or not -(2**31) <= counter < 2**31):
            raise ValueError("Legacy session counter BSON conversion is not verified")
        return PlaybackSession(id=_text(document["_id"]), heartbeat_count=counter, **fields)

    def save(self, session):
        document = {}
        for target, source in {**TEXT, **DATES}.items():
            value = getattr(session, target)
            if value is not None:
                document[source] = value
        for target, source in LONGS.items():
            value = getattr(session, target)
            if value is not None:
                document[source] = Int64(value)
        if session.heartbeat_count is not None:
            document["heartbeatCount"] = session.heartbeat_count
        collection = self._collection()
        if session.id is None:
            return str(collection.insert_one(document).inserted_id)
        key = ObjectId(session.id) if ObjectId.is_valid(session.id) else session.id
        document["_id"] = key
        collection.replace_one({"_id": key}, document, upsert=True)
        return str(key)
