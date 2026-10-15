"""Bounded legacy tsFile key lookup; never falls back from ObjectId to String."""

from video_serverless.adapters.mongo_hls import _key
from video_serverless.adapters.mongo_users import _text


class MongoFileRedirectKeys:
    def __init__(self, database_provider):
        self._database_provider = database_provider

    def key_for_file(self, file_id):
        record = self._database_provider().get_collection("tsFile").find_one(
            {"_id": _key(file_id)}, {"key": 1}
        )
        return None if record is None else _text(record.get("key"))
