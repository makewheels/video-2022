"""Keep all applicable LOCAL lanes persistent before clients finish individual renditions."""

import math
from dataclasses import dataclass
from datetime import tzinfo

from video_serverless.business.local_covers import LocalCoverError
from video_serverless.ports.local_covers import CoverClock, CoverUploads
from video_serverless.ports.local_transcodes import LocalTranscodeStore

_MEDIA = "width height videoCodec audioCodec bitrate displayWidth displayHeight frameRateMode sar rotation pixFmt " \
         "bitDepth colorPrimaries colorTransfer colorSpace colorRange dynamicRange audioTrackCount"


def _float32(value):
    mantissa, exponent = math.frexp(value)
    return math.ldexp(round(mantissa * 2**24), exponent - 24)


def output_size(width, height, resolution):
    target = {"720p": 720, "1080p": 1080}[resolution]
    if min(width, height) > target:
        if width >= height:
            value = _float32(_float32(_float32(width) * target) / _float32(height))
            width, height = min(2**31 - 1, math.floor(value + 0.5)), target
        else:
            value = _float32(_float32(_float32(height) * target) / _float32(width))
            width, height = target, min(2**31 - 1, math.floor(value + 0.5))
    # Java remainder truncates toward zero, including legacy zero/negative dimensions.
    return tuple(value if value % 2 == 0 else value - 1 if value > 0 else value + 1 for value in (width, height))


@dataclass(frozen=True)
class LocalTranscodes:
    store: LocalTranscodeStore
    uploads: CoverUploads
    clock: CoverClock
    local_timezone: tzinfo

    def create(self, user_id: str, request: dict) -> dict:
        video_id = request.get("videoId")
        for _ in range(2):
            if not self.store.video_exists(video_id):
                raise LocalCoverError(22, "视频不存在, videoId = " + ("null" if video_id is None else video_id))
        if not self.store.user_exists(user_id):
            raise LocalCoverError(14, "用户不存在, userId = " + ("null" if user_id is None else user_id))
        owned = self.store.video(video_id)
        if owned is None or owned.get("uploaderId") is None:
            raise RuntimeError("Video uploader disappeared during local transcode authorization")
        if owned["uploaderId"] != user_id:
            raise LocalCoverError(23, "视频不属于user, videoId = " + video_id + ", ownerId = "
                                  + owned["uploaderId"] + ", userId = " + user_id)
        video = self.store.video(video_id)
        if video is None:
            raise RuntimeError("Video disappeared after local transcode authorization")
        if video.get("transcodeMode") != "LOCAL":
            raise LocalCoverError(21, "该视频不是本地转码模式")
        if request.get("durationMs") is None:
            raise ValueError("durationMs 必填，用于计算码率")
        media = video.get("mediaInfo")
        if media is None:
            media = {}
            video["mediaInfo"] = media
        if media.get("duration") is None:
            media.update({name: request.get(name) for name in _MEDIA.split()})
            media["duration"] = request["durationMs"]
            if request.get("frameRateFraction") is not None:
                media["frameRate"] = request["frameRateFraction"]
            elif request.get("frameRateText") is not None:
                media["frameRate"] = request["frameRateText"]
        if video.get("status") != "READY":
            video["status"] = "TRANSCODING"
        width = request.get("width") if request.get("displayWidth") is None else request["displayWidth"]
        height = request.get("height") if request.get("displayHeight") is None else request["displayHeight"]
        resolutions = ["720p", "1080p"] if min(width, height) >= 1080 else ["720p"]
        if request.get("resolution") not in resolutions:
            raise LocalCoverError(21, "源片不适用该档位")
        ids = list(video.get("transcodeIds") or [])
        selected = None
        for resolution in resolutions:
            lane_id = "tr_local_" + video_id + "_" + resolution
            lane = self.store.transcode(lane_id)
            if lane is None:
                source = self.store.file(video.get("rawFileId"))
                month = video["createTime"].astimezone(self.local_timezone).strftime("%Y%m")
                prefix = f"videos/{video.get('uploaderId')}/{month}/{video_id}/transcode/{lane_id}"
                actual_width, actual_height = output_size(width, height, resolution)
                lane = {"_id": lane_id, "userId": video.get("uploaderId"), "videoId": video_id,
                        "provider": "LOCAL_TRANSCODE", "status": "TRANSCODING", "resolution": resolution,
                        "sourceKey": None if source is None else source.get("key"),
                        "m3u8Key": prefix + "/" + lane_id + ".m3u8", "createTime": self.clock(),
                        "actualWidth": actual_width, "actualHeight": actual_height}
                self.store.save_transcode(lane)
            if lane_id not in ids:
                ids.append(lane_id)
            if resolution == request.get("resolution"):
                selected = lane
        video["transcodeIds"] = ids
        self.store.save_video(video)
        key = selected.get("m3u8Key")
        output_dir = key[:key.rindex("/")]
        credentials = self.uploads.upload_credentials(key)
        if credentials is None:
            raise LocalCoverError(34)
        return {"transcodeId": selected["_id"], "resolution": selected.get("resolution"), "m3u8Key": key,
                "outputDir": output_dir, "tsFilenameTemplate": selected["_id"] + "-%05d.ts",
                "uploadCredentials": credentials}
