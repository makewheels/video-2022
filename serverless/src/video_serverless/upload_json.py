"""Spring creation DTO binding; internal DTO objects are validated then overwritten by the service."""

from video_serverless.heartbeat_json import _date, _long
from video_serverless.playback_json import _boolean, _spring_object
from video_serverless.ports.video_creation import CreateVideo
from video_serverless.profile_json import _Object, _string


def _fields(strings="", longs="", integers="", dates="", booleans="", objects=None):
    fields = {name: kind for kind, names in (("string", strings), ("long", longs), ("int", integers),
                                            ("date", dates), ("bool", booleans)) for name in names.split()}
    return {**fields, **(objects or {})}


_USER = _fields("id phone registerChannel token nickname avatarFileId avatarUrl bannerUrl bio",
                "subscriberCount videoCount", dates="createTime updateTime")
_FILE = _fields("id uploaderId videoId rawFilename videoType fileStatus linkFileId linkFileKey filename "
                "fileType key extension etag md5 acl provider storageClass", "size",
                dates="uploadTime deleteTime createTime updateTime", booleans="deleted hasLink")
_MEDIA = _fields("videoCodec audioCodec frameRate frameRateMode sar pixFmt colorPrimaries colorTransfer "
                 "colorSpace colorRange dynamicRange", "duration",
                 "width height bitrate displayWidth displayHeight rotation bitDepth audioTrackCount",
                 objects={"response": "map", "audioTracks": "map"})
_YOUTUBE = _fields("videoId url", dates="publishTime", objects={"videoInfo": "map"})
_STORAGE = _fields(dates="expireTime deleteTime", booleans="permanent rawFileDeleted transcodeFilesDeleted")
_WATCH = _fields("watchId watchUrl shortUrl", integers="watchCount", booleans="showUploadTime showWatchCount")
_LINK = _fields("linkVideoId", booleans="hasLink")
_VIDEO = _fields("id uploaderId ownerId title description category visibility videoType provider status "
                 "transcodeMode rawFileId coverId apiAppId", integers="likeCount dislikeCount commentCount",
                 dates="createTime updateTime", objects={"tags": "strings", "transcodeIds": "strings",
                 "youTube": _YOUTUBE, "mediaInfo": _MEDIA, "storageStatus": _STORAGE, "watch": _WATCH, "link": _LINK})
_SCHEMA = _fields("videoType rawFilename youtubeUrl ttl transcodeMode", "size",
                  objects={"user": _USER, "video": _VIDEO, "rawFile": _FILE})


class UploadBindingError(ValueError):
    pass


def _validate(value, kind):
    if value is None:
        return None
    if isinstance(kind, dict):
        if not isinstance(value, _Object):
            raise UploadBindingError("Creation DTO object binding failed")
        result = {}
        for name, item in value:
            if name in kind:
                result[name] = _validate(item, kind[name])
        return result
    if kind == "string":
        return _string(value)
    if kind in {"long", "int"}:
        result = _long(value)
        if kind == "int" and result is not None and not -(2**31) <= result < 2**31:
            raise UploadBindingError("Creation DTO integer binding failed")
        return result
    if kind == "bool":
        return _boolean(value)
    if kind == "date":
        return _date(value)
    if kind == "map" and isinstance(value, _Object):
        return None
    if kind == "strings" and isinstance(value, list) and not isinstance(value, _Object):
        return [_string(item) for item in value]
    raise UploadBindingError("Creation DTO binding failed")


def decode_creation(body: bytes, content_type: str | None = None) -> CreateVideo:
    try:
        fields = _validate(_spring_object(body, content_type), _SCHEMA)
        return CreateVideo(fields.get("videoType"), fields.get("rawFilename"), fields.get("size"),
                           fields.get("transcodeMode"))
    except (ValueError, UnicodeError, OverflowError, ArithmeticError, RecursionError):
        raise UploadBindingError("Creation DTO binding failed") from None
