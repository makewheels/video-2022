"""Existing video_like records, Spring ID conversion and independent narrow counter writes."""

from datetime import UTC

from bson.codec_options import CodecOptions

from video_serverless.adapters.mongo_users import _date, _long, _text
from video_serverless.adapters.mongo_video_status import _id
from video_serverless.ports.video_reactions import VideoReaction


class MongoVideoReactions:
    def __init__(self, database_provider):
        self._database_provider = database_provider

    def _collection(self):
        return self._database_provider().get_collection(
            "video_like", codec_options=CodecOptions(tz_aware=True, tzinfo=UTC))

    def video_exists(self, video_id):
        return self._database_provider().video.find_one({"_id": _id(video_id)}, {"_id": 1}) is not None

    def counts(self, video_id):
        row = self._database_provider().video.find_one({"_id": _id(video_id)}, {"likeCount": 1, "dislikeCount": 1})
        if row is None:
            return None
        likes, dislikes = (_long(row.get(name)) for name in ("likeCount", "dislikeCount"))
        if any(value is not None and not -(2**31) <= value < 2**31 for value in (likes, dislikes)):
            raise ValueError("Video reaction counter conversion outside Java int is not verified")
        return likes, dislikes

    def find_reaction(self, video_id, user_id):
        row = self._collection().find_one({"videoId": video_id, "userId": user_id})
        if row is None:
            return None
        return VideoReaction(_text(row["_id"]), _text(row.get("videoId")), _text(row.get("userId")),
                             _text(row.get("type")), _date(row.get("createTime")), _date(row.get("updateTime")))

    def save_reaction(self, reaction):
        fields = {"videoId": reaction.video_id, "userId": reaction.user_id, "type": reaction.reaction_type,
                  "createTime": reaction.create_time, "updateTime": reaction.update_time}
        document = {name: value for name, value in fields.items() if value is not None}
        if reaction.id is None:
            self._collection().insert_one(document)
        else:
            document["_id"] = _id(reaction.id)
            self._collection().replace_one({"_id": document["_id"]}, document, upsert=True)

    def remove_reaction(self, reaction_id):
        self._collection().delete_one({"_id": _id(reaction_id)})

    def increment_count(self, video_id, reaction_type, delta):
        field = "likeCount" if reaction_type == "LIKE" else "dislikeCount"
        self._database_provider().video.update_one({"_id": _id(video_id)}, {"$inc": {field: delta}})
