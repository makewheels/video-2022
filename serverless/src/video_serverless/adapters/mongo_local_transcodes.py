"""Shared legacy media documents plus existing transcode reads/replacement; no new indexes."""

from bson import Int64

from video_serverless.adapters.mongo_local_covers import MongoLocalCovers, _id
from video_serverless.adapters.mongo_users import _long, _text

_FIELDS = frozenset("_id userId videoId jobId provider createTime finishTime status resolution width height "
                    "averageBitrate maxBitrate sourceKey m3u8Key result m3u8Content tsFileIds taskId currentProvider "
                    "fallbackCount errorMessage actualWidth actualHeight actualFrameRate "
                    "actualDynamicRange actualCodecs".split())
_TEXT = frozenset("_id userId videoId jobId provider status resolution sourceKey m3u8Key m3u8Content taskId "
                  "currentProvider errorMessage actualFrameRate actualDynamicRange actualCodecs".split())
_INTEGERS = frozenset("width height averageBitrate maxBitrate fallbackCount actualWidth actualHeight".split())


def _integer(value):
    result = _long(value)
    if result is not None and not -(2**31) <= result < 2**31:
        raise ValueError("Legacy media integer exceeds signed integer range")
    return result


class MongoLocalTranscodes(MongoLocalCovers):
    def transcode(self, transcode_id):
        raw = self._collection("transcode").find_one({"_id": _id(transcode_id)})
        if raw is None:
            return None
        document = {"createTime": self._clock(), "status": "CREATED"} | {
            name: value for name, value in raw.items() if name in _FIELDS}
        for name in _TEXT:
            if name in document:
                document[name] = _text(document[name])
        for name in _INTEGERS:
            if name in document:
                document[name] = _integer(document[name])
        return document

    def save_transcode(self, document):
        self._save("transcode", document, _FIELDS)

    def save_video(self, document):
        # Spring persists mediaInfo.duration as a BSON Long, even for small client values.
        media = document.get("mediaInfo")
        if media is not None and media.get("duration") is not None:
            document = document | {"mediaInfo": media | {"duration": Int64(media["duration"])}}
        super().save_video(document)
