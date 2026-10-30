"""Existing File/Transcode mapping plus ordered TsFile inserts; no new schema, index or retries."""

from bson import Int64

from video_serverless.adapters.mongo_local_covers import _id, _without_nulls
from video_serverless.adapters.mongo_local_transcodes import MongoLocalTranscodes, _integer
from video_serverless.adapters.mongo_users import _long, _text

_FIELDS = frozenset("_id uploaderId videoId videoType transcodeId resolution tsIndex bitrate timeLength fileStatus "
                    "filename fileType key extension size etag md5 acl provider storageClass uploadTime deleteTime "
                    "createTime updateTime deleted".split())
_TEXT = frozenset("_id uploaderId videoId videoType transcodeId resolution fileStatus filename fileType key "
                  "extension etag md5 acl provider storageClass".split())


def _mapped(document):
    if document is None:
        return None
    result = {name: value for name, value in document.items() if name in _FIELDS}
    for name in _TEXT:
        if name in result:
            result[name] = _text(result[name])
    for name in ("tsIndex", "bitrate"):
        if name in result:
            result[name] = _integer(result[name])
    if "size" in result:
        result["size"] = _long(result["size"])
    return result


class MongoOutputRegistration(MongoLocalTranscodes):
    def video(self, video_id):
        document = super().video(video_id)
        if document is not None and document.get("mediaInfo") is not None:
            media = document["mediaInfo"]
            if "duration" in media:
                document["mediaInfo"] = media | {"duration": _long(media["duration"])}
        return document

    def file_by_key(self, key):
        return self._files.find_by_key(key)

    def segment(self, transcode_id, index):
        return _mapped(self._collection("tsFile").find_one({"transcodeId": transcode_id, "tsIndex": index}))

    def segments(self, transcode_id):
        return [_mapped(item) for item in self._collection("tsFile").find({"transcodeId": transcode_id})]

    def insert_segments(self, documents):
        saved = []
        for document in documents:
            row = _without_nulls({name: value for name, value in document.items() if name in _FIELDS})
            row["_id"] = _id(row["_id"])
            if "size" in row:
                row["size"] = Int64(row["size"])
            saved.append(row)
        self._collection("tsFile").insert_many(saved, ordered=True)
