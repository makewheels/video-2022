"""Read existing video, transcode and tsFile documents without writes or index changes."""

from bson import ObjectId

from video_serverless.adapters.mongo_users import _long, _text
from video_serverless.ports.hls import HlsAudio, HlsFile, HlsTranscode, HlsVideo


def _key(value):
    return ObjectId(value) if value is not None and ObjectId.is_valid(value) else value


class MongoHlsReads:
    def __init__(self, database_provider):
        self._database_provider = database_provider

    def video(self, video_id):
        document = self._database_provider().get_collection("video").find_one({"_id": _key(video_id)})
        if document is None:
            return None
        link = document.get("link") or {}
        ids = document.get("transcodeIds")
        return HlsVideo(_text(document["_id"]), _text(document.get("uploaderId")),
                        _text(document.get("visibility")), link.get("hasLink") is True,
                        _text(link.get("linkVideoId")), None if ids is None else tuple(map(_text, ids)))

    def transcode(self, transcode_id):
        document = self._database_provider().get_collection("transcode").find_one({"_id": _key(transcode_id)})
        if document is None:
            return None
        return self._transcode(document)

    @staticmethod
    def _transcode(document):
        ids = document.get("tsFileIds")
        return HlsTranscode(_text(document["_id"]), _text(document.get("videoId")),
                            _text(document.get("resolution")), None if ids is None else tuple(map(_text, ids)),
                            _text(document.get("m3u8Content")),
                            *[_text(document.get(key)) for key in ("provider", "currentProvider", "status")],
                            *[_long(document.get(key)) for key in (
                                "fallbackCount", "maxBitrate", "averageBitrate", "width", "height",
                                "actualWidth", "actualHeight")],
                            *[_text(document.get(key)) for key in (
                                "actualCodecs", "actualFrameRate", "actualDynamicRange")])

    def transcodes(self, transcode_ids):
        documents = self._database_provider().get_collection("transcode").find(
            {"_id": {"$in": [_key(value) for value in transcode_ids]}}
        )
        return [self._transcode(document) for document in documents]

    def audio(self, video_id):
        document = self._database_provider().get_collection("video").find_one({"_id": _key(video_id)})
        media = None if document is None else document.get("mediaInfo")
        if media is None:
            return None
        audio = media.get("audioTracks")
        tracks = None if audio is None else audio.get("tracks")
        return HlsAudio(_long(media.get("audioTrackCount")), None if tracks is None else tuple(
            (_text(track.get("language")), _long(track.get("channels"))) for track in tracks
        ))

    def files(self, file_ids):
        documents = self._database_provider().get_collection("tsFile").find(
            {"_id": {"$in": [_key(value) for value in file_ids]}}
        )
        return [HlsFile(_text(item["_id"]), _text(item.get("videoId")), _text(item.get("transcodeId")),
                        _text(item.get("filename")), _long(item.get("tsIndex")), _text(item.get("fileType")))
                for item in documents]
