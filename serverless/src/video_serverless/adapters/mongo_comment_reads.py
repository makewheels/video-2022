"""Legacy comment fields, null-parent filter and count/page order using the existing database."""

from datetime import UTC

from bson import ObjectId
from bson.codec_options import CodecOptions

from video_serverless.adapters.mongo_users import _date, _long, _text

_TEXT = frozenset("videoId userId userPhone userNickname userAvatarUrl content parentId replyToUserId "
                  "replyToUserPhone replyToUserNickname".split())


class MongoCommentReads:
    def __init__(self, database_provider):
        self._database_provider = database_provider

    def _collection(self):
        return self._database_provider().get_collection(
            "comment", codec_options=CodecOptions(tz_aware=True, tzinfo=UTC))

    def video_exists(self, video_id):
        key = ObjectId(video_id) if ObjectId.is_valid(video_id) else video_id
        return self._database_provider().video.find_one({"_id": key}, {"_id": 1}) is not None

    def count_top(self, video_id):
        return self._collection().count_documents({"videoId": video_id, "parentId": None})

    def top(self, video_id, skip, limit, sort_by):
        sort = [("likeCount", -1), ("createTime", -1)] if sort_by == "likeCount" else [("createTime", -1)]
        return self._page({"videoId": video_id, "parentId": None}, sort, skip, limit)

    def replies(self, parent_id, skip, limit):
        return self._page({"parentId": parent_id}, [("createTime", 1)], skip, limit)

    def count_all(self, video_id):
        return self._collection().count_documents({"videoId": video_id})

    def _page(self, query, sort, skip, limit):
        cursor = self._collection().find(query).sort(sort).skip(max(skip, 0)).limit(max(limit, 0))
        with cursor:
            return tuple(self._map(document) for document in cursor)

    def _map(self, document):
        row = {"id": _text(document["_id"])}
        row.update({name: _text(document.get(name)) for name in _TEXT})
        row.update({name: _date(document.get(name)) for name in ("createTime", "updateTime")})
        for name in ("likeCount", "replyCount"):
            value = _long(document.get(name))
            if value is not None and not -(2**31) <= value < 2**31:
                raise ValueError("Comment counter conversion outside Java int is not verified")
            row[name] = value
        return row
