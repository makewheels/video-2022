"""Read the existing collections and preserve missing-vs-null status defaults."""

from bson import ObjectId

from video_serverless.adapters.mongo_users import _text
from video_serverless.ports.video_status import VideoStatusSnapshot


def _id(value):
    return ObjectId(value) if ObjectId.is_valid(value) else value


class MongoVideoStatus:
    def __init__(self, database_provider):
        self._database_provider = database_provider

    def video_exists(self, video_id):
        return self._database_provider().video.find_one({"_id": _id(video_id)}, {"_id": 1}) is not None

    def user_exists(self, user_id):
        return self._database_provider().user.find_one({"_id": _id(user_id)}, {"_id": 1}) is not None

    def find(self, video_id):
        document = self._database_provider().video.find_one({"_id": _id(video_id)})
        if document is None:
            return None
        return VideoStatusSnapshot(str(document["_id"]), _text(document.get("uploaderId")),
                                   _text(document.get("status", "CREATED")))
