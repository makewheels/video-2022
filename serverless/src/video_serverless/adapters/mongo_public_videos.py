"""Legacy PUBLIC filter, Java trim/literal search, paging and normal BSON projection."""

import re
from datetime import UTC

from bson.codec_options import CodecOptions

from video_serverless.adapters.mongo_users import _date, _long, _text
from video_serverless.ports.public_videos import PublicVideo


def _filter(keyword):
    query = {"visibility": "PUBLIC"}
    # Java String.trim only removes characters <= U+0020, unlike Python str.strip().
    trimmed = keyword.strip("".join(chr(value) for value in range(33))) if keyword is not None else ""
    if trimmed:
        query["$or"] = [{name: {"$regex": re.escape(trimmed), "$options": "i"}}
                        for name in ("title", "description", "tags")]
    return query


class MongoPublicVideos:
    def __init__(self, database_provider, clock):
        self._database_provider = database_provider
        self._clock = clock

    def _collection(self):
        return self._database_provider().get_collection("video", codec_options=CodecOptions(tz_aware=True, tzinfo=UTC))

    def page(self, skip, limit, keyword):
        return self._page(_filter(keyword), skip, limit)

    def _page(self, query, skip, limit):
        # Spring Query.limit(int) treats <=0 as unlimited; negative skip is ignored by the template.
        cursor = self._collection().find(query).sort("createTime", -1)
        cursor = cursor.skip(max(skip, 0)).limit(max(limit, 0))
        with cursor:
            return tuple(self._map(document) for document in cursor)

    def count(self, keyword):
        return self._collection().count_documents(_filter(keyword))

    def _map(self, document):
        watch = document.get("watch") or {}
        media = document.get("mediaInfo") or {}
        youtube = document.get("youTube") or {}
        tags = document.get("tags", [])
        if tags is not None and not isinstance(tags, list):
            raise ValueError("Video tag BSON shape has not been verified")
        if not all(isinstance(value, dict) for value in (watch, media, youtube)):
            raise ValueError("Video nested BSON shape has not been verified")
        watch_count = _long(watch.get("watchCount", 0))
        if watch_count is not None and not -(2**31) <= watch_count < 2**31:
            raise ValueError("Video watch count is outside the Java int range")
        return PublicVideo(
            _text(document["_id"]), _text(document.get("uploaderId")), _text(document.get("title")),
            _text(document.get("description")), _text(document.get("status", "CREATED")),
            _text(document.get("visibility")), None if tags is None else tuple(_text(tag) for tag in tags),
            _text(document.get("category")),
            _date(document["createTime"] if "createTime" in document else self._clock()),
            _text(document.get("coverId")), watch_count,
            _text(watch.get("watchId")), _text(watch.get("watchUrl")), _text(watch.get("shortUrl")),
            _long(media.get("duration")),
            _date(youtube.get("publishTime")) if document.get("videoType") == "YOUTUBE" else None,
        )
