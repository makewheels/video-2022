"""The legacy LOCAL prefix: validate and persist outputs, never finalize a video or dispatch work."""

from dataclasses import dataclass
from decimal import Decimal
from typing import Callable

from video_serverless.business.hls import _lines, _trim, init_segment_uri
from video_serverless.business.output_validation import OutputValidation
from video_serverless.ports.local_covers import CoverClock, CoverIds
from video_serverless.ports.local_output_registration import RegistrationObjects, RegistrationStore, StoredOutput
from video_serverless.ports.output_validation import OutputManifest


def _basename(key):
    return key[max(key.rfind("/"), key.rfind("\\")) + 1:]


def _folder(key):
    return key[:max(key.rfind("/"), key.rfind("\\")) + 1]


def _long(value):
    return (value + 2**63) % 2**64 - 2**63


def _bitrate(size, duration):
    # Java wraps the long multiplication, then rounds the exact quotient to scale zero.
    numerator, denominator = duration.as_integer_ratio()
    if numerator == 0:
        raise ZeroDivisionError("Zero media duration")
    dividend = _long(size * 8) * denominator
    quotient, remainder = divmod(abs(dividend), abs(numerator))
    result = (quotient + (remainder * 2 >= abs(numerator))) * (-1 if (dividend < 0) != (numerator < 0) else 1)
    if not -(2**31) <= result < 2**31:
        raise OverflowError("Legacy bitrate exceeds signed integer range")
    return result


def _durations(content):
    lines = _lines(content)
    # The original preallocates an ArrayList with (lineCount - 5) / 2.
    if len(lines) < 4:
        raise ValueError("Invalid legacy playlist capacity")
    durations = {}
    for index, line in enumerate(lines):
        if line.startswith("#EXTINF:"):
            if "," not in line:
                raise ValueError("Missing EXTINF comma")
            text = line[len("#EXTINF:"):line.index(",")]
            duration = Decimal(text)
            if not duration.is_finite() or "_" in text or any(char.isspace() for char in text):
                raise ValueError("Invalid EXTINF duration")
            durations[lines[index + 1]] = duration
    return durations


def _object_fields(output: StoredOutput):
    name = _basename(output.key)
    return {"key": output.key, "filename": name, "extension": name.rsplit(".", 1)[1] if "." in name else "",
            "size": output.size, "etag": output.etag, "storageClass": output.storage_class,
            "uploadTime": output.upload_time}


def _dimension(expected, actual):
    if expected is None:
        raise ValueError("LOCAL task dimension is null")
    return expected == actual


@dataclass(frozen=True)
class LocalOutputRegistration:
    store: RegistrationStore
    objects: RegistrationObjects
    ids: CoverIds
    clock: CoverClock
    warning: Callable[[str], None]

    def register(self, transcode: dict) -> None:
        key = transcode["m3u8Key"]
        sidecar = self.objects.read_sidecar(_folder(key) + "result.json")
        if sidecar is not None:
            if (sidecar.integer("schemaVersion", primitive=True) != 1
                    or transcode["_id"] != sidecar.text("transcodeId")
                    or _basename(key) != sidecar.text("playlistFilename")
                    or not _dimension(transcode.get("actualWidth"), sidecar.integer("width"))
                    or not _dimension(transcode.get("actualHeight"), sidecar.integer("height"))):
                raise RuntimeError("本地输出媒体参数与任务不符")
            transcode["actualWidth"] = sidecar.integer("width")
            transcode["actualHeight"] = sidecar.integer("height")
            transcode["actualFrameRate"] = sidecar.text("frameRate")
            transcode["actualDynamicRange"] = sidecar.text("dynamicRange")
            codecs = sidecar.text("hlsCodecs")
            if codecs is not None and (not codecs or any(char not in
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789.," for char in codecs)):
                raise RuntimeError("播放 codec 参数格式非法")
            transcode["actualCodecs"] = codecs
        error = OutputValidation(self.objects).validate(key, OutputManifest(key))
        if error is not None:
            raise RuntimeError(error)
        video = self.store.video(transcode["videoId"])
        self._playlist(video, transcode)
        self._segments(video, transcode)

    def _playlist(self, video, transcode):
        key = transcode["m3u8Key"]
        transcode["m3u8Content"] = self.objects.signed_playlist(key)
        if self.store.file_by_key(key) is not None:
            return
        now = self.clock()
        file_id = self.ids.long_id("f_")
        if video is None:
            raise ValueError("Video does not exist")
        document = {"_id": file_id, "createTime": now, "updateTime": now, "deleted": False,
                    "fileStatus": "READY", "provider": "ALIYUN_OSS", "hasLink": False,
                    "key": key, "fileType": "TRANSCODE_M3U8", "videoId": video["_id"],
                    "videoType": video.get("videoType"), "uploaderId": video.get("uploaderId")}
        metadata = self.objects.metadata(key)
        if metadata is None:
            raise ValueError("Playlist object does not exist")
        document.update(_object_fields(metadata))
        if file_id is None:
            raise ValueError("m3u8File id is null")
        self.store.save_file(document)

    def _segment(self, video, transcode, index, duration, output):
        if self.store.segment(transcode["_id"], index) is not None:
            return None
        if output is None:
            raise RuntimeError("OSS 分片登记不完整，index=" + str(index))
        file_id = self.ids.long_id("f_ts_")
        if video is None:
            raise ValueError("Video does not exist")
        document = {"_id": file_id, "fileStatus": "READY", "fileType": "TRANSCODE_TS",
                    "uploaderId": video.get("uploaderId"), "videoId": video["_id"], "videoType": video.get("videoType"),
                    "transcodeId": transcode["_id"], "resolution": transcode.get("resolution"), "tsIndex": index}
        document.update(_object_fields(output))
        if duration is not None and document.get("size") is not None:
            document["bitrate"] = _bitrate(document["size"], duration)
        return document

    def _segments(self, video, transcode):
        outputs = {}
        for output in self.objects.list_outputs(_folder(transcode["m3u8Key"])):
            name = _basename(output.key)
            if name in outputs:
                raise RuntimeError("Duplicate key " + name)
            outputs[name] = output
        content = transcode["m3u8Content"]
        names = [_trim(line) for line in _lines(content) if _trim(line) and not _trim(line).startswith("#")]
        durations = _durations(content)
        new_files = []
        init = init_segment_uri(content)
        if init is not None:
            new_files.append(self._segment(video, transcode, -1, None, outputs.get(init)))
        for index, name in enumerate(names):
            new_files.append(self._segment(video, transcode, index, durations.get(name), outputs.get(name)))
        new_files = [item for item in new_files if item is not None and item.get("_id") is not None]
        if new_files:
            self.store.insert_segments(new_files)
        all_files = self.store.segments(transcode["_id"])
        transcode["tsFileIds"] = [item.get("_id") for item in all_files]
        segments = [item for item in all_files if item.get("tsIndex") is not None
                    and item["tsIndex"] >= 0 and item.get("size") is not None]
        if segments and video is None:
            raise ValueError("Video does not exist")
        media = None if video is None else video.get("mediaInfo")
        if segments and media is not None and media.get("duration") is not None:
            transcode["averageBitrate"] = _bitrate(_long(sum(item["size"] for item in segments)),
                                                  Decimal(str(media["duration"]) + "e-3"))
            transcode["maxBitrate"] = max((item["bitrate"] for item in segments
                                          if item.get("bitrate") is not None), default=None)
        else:
            self.warning("缺少时长或分片数据，跳过码率计算 transcodeId = " + transcode["_id"])
        self.store.save_transcode(transcode)
