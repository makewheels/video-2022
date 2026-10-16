"""Legacy ordinary video creation; no cloud submissions or implicit transaction."""

from dataclasses import dataclass
from datetime import tzinfo

from video_serverless.ports.video_creation import (
    CreatedVideo,
    CreateVideo,
    CreationClock,
    CreationIds,
    CreationWrites,
)


class CreationArgumentError(ValueError):
    pass


@dataclass(frozen=True)
class VideoCreation:
    writes: CreationWrites
    ids: CreationIds
    clock: CreationClock
    local_timezone: tzinfo
    internal_base_url: str

    def create(self, user_id: str, request: CreateVideo) -> CreatedVideo:
        if not request.video_type:
            raise CreationArgumentError("视频创建参数，视频类型videoType为空")
        if request.video_type == "USER_UPLOAD":
            if not request.raw_filename:
                raise CreationArgumentError("视频创建参数，原始文件名rawFilename为空")
            if request.size is None or request.size == 0:
                raise CreationArgumentError("视频创建参数，原始文件大小size为空")
        # YouTube has a separate external-work submission flow, not ordinary creation.
        if request.video_type == "YOUTUBE":
            raise NotImplementedError("YouTube creation requires its own task orchestration")
        now = self.clock()
        updated = self.clock()
        video_id = self.ids.long_id("v_")
        local_month = now.astimezone(self.local_timezone).strftime("%Y%m")
        video = {
            "_id": video_id, "uploaderId": user_id, "ownerId": user_id,
            "tags": [], "visibility": "PUBLIC", "videoType": request.video_type,
            "provider": "ALIYUN_OSS", "status": "CREATED",
            "transcodeMode": "LOCAL" if request.transcode_mode == "LOCAL" else "AUTO",
            "youTube": {}, "mediaInfo": {},
            "storageStatus": {"permanent": True, "rawFileDeleted": False, "transcodeFilesDeleted": False},
            "link": {"hasLink": False}, "createTime": now, "updateTime": updated,
        }
        file_now = self.clock()
        file_updated = self.clock()
        file_id = self.ids.long_id("f_")
        file = {
            "_id": file_id, "uploaderId": user_id, "videoType": request.video_type,
            "fileStatus": "CREATED", "hasLink": False, "fileType": "RAW_VIDEO",
            "provider": "ALIYUN_OSS", "createTime": file_now, "updateTime": file_updated, "deleted": False,
        }
        extension = None
        if request.video_type == "USER_UPLOAD":
            name = request.raw_filename
            if "\x00" in name:
                raise ValueError("Filename contains a null character")
            # Commons IO ignores dots before the final slash or backslash.
            dot = name.rfind(".")
            extension = name[dot + 1:].lower() if dot > max(name.rfind("/"), name.rfind("\\")) else ""
            file.update(rawFilename=name, extension=extension)
        self.writes.save_file(file)
        watch_id = self.ids.short_id()
        url = self.internal_base_url + "/w?v=" + watch_id
        video["rawFileId"] = file_id
        video["watch"] = {"watchId": watch_id, "watchUrl": url, "shortUrl": url,
                          "watchCount": 0, "showUploadTime": True, "showWatchCount": True}
        self.writes.save_video(video)
        file["videoId"] = video_id
        suffix = "null" if extension is None else extension
        file["key"] = f"videos/{user_id}/{local_month}/{video_id}/raw/{file_id}/{file_id}.{suffix}"
        self.writes.save_file(file)
        return CreatedVideo(video_id, file_id, watch_id, url)
