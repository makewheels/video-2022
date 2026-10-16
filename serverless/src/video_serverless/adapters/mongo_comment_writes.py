"""Existing comment collections, generated ObjectId IDs and ordered narrow counter operations."""

from bson import ObjectId

from video_serverless.adapters.mongo_comment_reads import MongoCommentReads
from video_serverless.adapters.mongo_users import _text


def _key(identifier):
    if identifier is None:
        raise ValueError("Legacy comment entity ID must not be null")
    return ObjectId(identifier) if ObjectId.is_valid(identifier) else identifier


class MongoCommentWrites(MongoCommentReads):
    def video_owner(self, video_id):
        row = self._database_provider().video.find_one({"_id": _key(video_id)}, {"uploaderId": 1})
        return None if row is None else _text(row.get("uploaderId"))

    def comment(self, comment_id):
        row = self._collection().find_one({"_id": _key(comment_id)})
        return None if row is None else self._map(row)

    def create_comment(self, document):
        saved = {name: value for name, value in document.items() if name != "id" and value is not None}
        return str(self._collection().insert_one(saved).inserted_id)

    def all_replies(self, parent_id):
        with self._collection().find({"parentId": parent_id}) as cursor:
            return tuple(self._map(row) for row in cursor)

    def increment_replies(self, parent_id, delta):
        self._collection().update_one({"_id": _key(parent_id)}, {"$inc": {"replyCount": delta}})

    def increment_video_comments(self, video_id, delta):
        self._database_provider().video.update_one({"_id": _key(video_id)}, {"$inc": {"commentCount": delta}})

    def remove_comment(self, comment_id):
        self._collection().delete_one({"_id": _key(comment_id)})

    def remove_replies(self, parent_id):
        self._collection().delete_many({"parentId": parent_id})

    def remove_likes(self, comment_id):
        self._database_provider().comment_like.delete_many({"commentId": comment_id})

    def find_like(self, comment_id, user_id):
        row = self._database_provider().comment_like.find_one({"commentId": comment_id, "userId": user_id})
        return None if row is None else {"id": _text(row["_id"])}

    def create_like(self, document):
        saved = {name: value for name, value in document.items() if value is not None}
        return str(self._database_provider().comment_like.insert_one(saved).inserted_id)

    def remove_like(self, like_id):
        self._database_provider().comment_like.delete_one({"_id": _key(like_id)})

    def increment_likes(self, comment_id, delta):
        self._collection().update_one({"_id": _key(comment_id)}, {"$inc": {"likeCount": delta}})
