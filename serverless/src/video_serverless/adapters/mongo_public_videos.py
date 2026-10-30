"""Legacy PUBLIC filter, Java trim/literal search, paging and normal BSON projection."""

import json
import math
import re
from datetime import UTC, datetime, timedelta

from bson import json_util
from bson.codec_options import CodecOptions

from video_serverless.adapters.mongo_users import _date, _long, _text
from video_serverless.java_numbers import double_text
from video_serverless.ports.public_videos import PublicVideo

_JAVA_TRIM = "".join(chr(value) for value in range(33))


def _filter(keyword):
    query = {"visibility": "PUBLIC"}
    # Java String.trim only removes characters <= U+0020, unlike Python str.strip().
    trimmed = keyword.strip(_JAVA_TRIM) if keyword is not None else ""
    if trimmed:
        query["$or"] = [{name: {"$regex": re.escape(trimmed), "$options": "i"}}
                        for name in ("title", "description", "tags")]
    return query


def _double_text(value):
    text = double_text(value)
    # JDK scientific form chooses the closest decimal with at least two significant digits.
    if re.fullmatch(r"-?\d\.0E[+-]?\d+", text):
        mantissa, exponent = format(value, ".1e").split("e")
        return mantissa + "E" + str(int(exponent))
    return text


def _document_text(value):
    # Spring's BSON Document -> String converter uses relaxed JSON and escaped UTF-16 surrogate units.
    if isinstance(value, dict):
        return "{" + ", ".join(_document_text(key) + ": " + _document_text(item) for key, item in value.items()) + "}"
    if isinstance(value, list):
        return "[" + ", ".join(_document_text(item) for item in value) + "]"
    if isinstance(value, float) and math.isfinite(value):
        return _double_text(value)
    if value is None or isinstance(value, (str, bool, int)):
        text = json.dumps(value, ensure_ascii=False)
        return "".join(
            char if ord(char) <= 0xFFFF else
            f"\\u{0xD800 + ((ord(char) - 0x10000) >> 10):04x}\\u{0xDC00 + ((ord(char) - 0x10000) & 0x3FF):04x}"
            for char in text
        )
    return _document_text(json_util.default(value, json_options=json_util.RELAXED_JSON_OPTIONS))


def _video_text(value):
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, float):
        if math.isnan(value):
            return "NaN"
        if math.isinf(value):
            return "-Infinity" if value < 0 else "Infinity"
        return _double_text(value)
    if isinstance(value, dict):
        return _document_text(value)
    if isinstance(value, list):
        return ",".join("null" if item is None else _video_text(item) for item in value)
    return _text(value)


def _nested(document, name):
    value = document.get(name, {})
    if value is not None and not isinstance(value, dict):
        raise ValueError("Video nested BSON shape does not match the legacy object mapping")
    return value


def _tags(value):
    if value is None:
        return None
    if isinstance(value, str):
        value = [] if value == "" else [item.strip(_JAVA_TRIM) for item in value.split(",")]
    if not isinstance(value, list):
        raise ValueError("Video tag BSON shape does not match the legacy collection mapping")
    return tuple(_video_text(item) for item in value)


def _video_date(value):
    if isinstance(value, int) and not isinstance(value, bool):
        return datetime(1970, 1, 1, tzinfo=UTC) + timedelta(milliseconds=value)
    return _date(value)


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
        raw_watch = _nested(document, "watch")
        watch = {} if raw_watch is None else raw_watch
        media = _nested(document, "mediaInfo")
        youtube = _nested(document, "youTube")
        watch_count = None if raw_watch is None else _long(watch.get("watchCount", 0))
        if watch_count is not None and not -(2**31) <= watch_count < 2**31:
            raise ValueError("Video watch count is outside the Java int range")
        return PublicVideo(
            _video_text(document["_id"]), _video_text(document.get("uploaderId")), _video_text(document.get("title")),
            _video_text(document.get("description")), _video_text(document.get("status", "CREATED")),
            _video_text(document.get("visibility")), _tags(document.get("tags", [])),
            _video_text(document.get("category")),
            _video_date(document["createTime"] if "createTime" in document else self._clock()),
            _video_text(document.get("coverId")), watch_count,
            _video_text(watch.get("watchId")), _video_text(watch.get("watchUrl")), _video_text(watch.get("shortUrl")),
            _long(None if media is None else media.get("duration")),
            _video_date(None if youtube is None else youtube.get("publishTime"))
            if document.get("videoType") == "YOUTUBE" else None,
        )
