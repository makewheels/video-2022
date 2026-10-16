"""Legacy Cover/File/Video mapping and replacement writes for isolated local registration."""

from datetime import UTC, datetime

from bson import ObjectId
from bson.codec_options import CodecOptions

from video_serverless.adapters.mongo_file_uploads import MongoUploadFiles
from video_serverless.adapters.mongo_users import _text

_COVER = frozenset("_id userId videoId provider fileId jobId createTime finishTime status "
                   "accessUrl extension key result".split())
_VIDEO = frozenset("_id uploaderId ownerId title description tags category visibility videoType provider status "
                   "transcodeMode rawFileId coverId apiAppId transcodeIds youTube mediaInfo storageStatus watch link "
                   "likeCount dislikeCount commentCount createTime updateTime".split())
_NESTED = {
    "mediaInfo": frozenset("width height videoCodec audioCodec bitrate duration response displayWidth displayHeight "
                           "frameRate frameRateMode sar rotation pixFmt bitDepth colorPrimaries colorTransfer "
                           "colorSpace colorRange dynamicRange audioTrackCount audioTracks".split()),
    "youTube": frozenset("videoId url videoInfo publishTime".split()),
    "watch": frozenset("watchId watchUrl shortUrl watchCount showUploadTime showWatchCount".split()),
    "storageStatus": frozenset("expireTime permanent rawFileDeleted transcodeFilesDeleted deleteTime".split()),
    "link": frozenset("hasLink linkVideoId".split()),
}


def _id(value):
    if value is None:
        raise ValueError("Legacy entity ID must not be null")
    return ObjectId(value) if ObjectId.is_valid(value) else value


def _without_nulls(document):
    # Typed object null fields are omitted; dynamic JSON maps retain their own null entries.
    return {name: value for name, value in document.items() if value is not None}


class MongoLocalCovers:
    def __init__(self, database_provider, clock=None):
        self._database_provider = database_provider
        self._clock = clock or (lambda: datetime.now(UTC))
        self._files = MongoUploadFiles(database_provider, self._clock)

    def _collection(self, name):
        return self._database_provider().get_collection(name, codec_options=CodecOptions(tz_aware=True, tzinfo=UTC))

    def video_exists(self, video_id):
        key = None if video_id is None else _id(video_id)
        return self._collection("video").find_one({"_id": key}, {"_id": 1}) is not None

    def user_exists(self, user_id):
        key = None if user_id is None else _id(user_id)
        return self._collection("user").find_one({"_id": key}, {"_id": 1}) is not None

    def video(self, video_id):
        raw = self._collection("video").find_one({"_id": _id(video_id)})
        if raw is None:
            return None
        now = self._clock()
        defaults = {"createTime": now, "updateTime": now, "status": "CREATED", "provider": "ALIYUN_OSS",
                    "tags": [], "youTube": {}, "mediaInfo": {},
                    "storageStatus": {"permanent": True, "rawFileDeleted": False, "transcodeFilesDeleted": False},
                    "watch": {"watchCount": 0, "showUploadTime": True, "showWatchCount": True},
                    "link": {"hasLink": False}}
        document = defaults | {name: value for name, value in raw.items() if name in _VIDEO}
        for name in ("_id", "uploaderId", "ownerId", "coverId", "rawFileId", "videoType", "transcodeMode"):
            if name in document:
                document[name] = _text(document[name])
        for name in ("watch", "storageStatus", "link"):
            if isinstance(document.get(name), dict):
                document[name] = defaults[name] | document[name]
        for name, fields in _NESTED.items():
            if isinstance(document.get(name), dict):
                document[name] = {key: value for key, value in document[name].items() if key in fields}
        return document

    def cover(self, cover_id):
        raw = self._collection("cover").find_one({"_id": _id(cover_id)})
        if raw is None:
            return None
        document = {"createTime": self._clock()} | {name: value for name, value in raw.items() if name in _COVER}
        for name in _COVER - {"createTime", "finishTime", "result"}:
            if name in document:
                document[name] = _text(document[name])
        return document

    def file(self, file_id):
        if file_id is None:
            raise ValueError("Legacy file ID must not be null")
        return self._files.find(file_id)

    def save_file(self, document):
        self._files.save(document)

    def _save(self, collection, document, fields):
        saved = _without_nulls({name: value for name, value in document.items() if name in fields})
        if collection == "video":
            for name in _NESTED:
                if isinstance(saved.get(name), dict):
                    saved[name] = _without_nulls(saved[name])
        saved["_id"] = _id(saved["_id"])
        self._collection(collection).replace_one({"_id": saved["_id"]}, saved, upsert=True)

    def save_video(self, document):
        self._save("video", document, _VIDEO)

    def save_cover(self, document):
        self._save("cover", document, _COVER)
