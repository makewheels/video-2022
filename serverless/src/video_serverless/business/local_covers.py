"""Legacy local cover metadata registration, with the original partial-write order."""

from dataclasses import dataclass
from datetime import tzinfo

from video_serverless.ports.avatars import UploadCredentials
from video_serverless.ports.local_covers import CoverClock, CoverIds, CoverUploads, LocalCoverStore


class LocalCoverError(RuntimeError):
    def __init__(self, code, detail=None):
        super().__init__(*(() if detail is None else (detail,)))
        self.code, self.detail = code, detail


@dataclass(frozen=True)
class CreatedLocalCover:
    id: str
    key: str
    credentials: UploadCredentials


@dataclass(frozen=True)
class LocalCovers:
    store: LocalCoverStore
    uploads: CoverUploads
    ids: CoverIds
    clock: CoverClock
    local_timezone: tzinfo
    access_base_url: str

    def _owned(self, video_id, user_id):
        if not self.store.video_exists(video_id):
            raise LocalCoverError(22, "视频不存在, videoId = " + ("null" if video_id is None else video_id))
        if not self.store.user_exists(user_id):
            raise LocalCoverError(14, "用户不存在, userId = " + ("null" if user_id is None else user_id))
        video = self.store.video(video_id)
        if video is None or video.get("uploaderId") is None:
            raise RuntimeError("Video uploader disappeared during local cover authorization")
        if video["uploaderId"] != user_id:
            raise LocalCoverError(23, "视频不属于user, videoId = " + video_id + ", ownerId = "
                                  + video["uploaderId"] + ", userId = " + user_id)

    def create(self, user_id: str, video_id: str, extension: str | None) -> CreatedLocalCover:
        if not self.store.video_exists(video_id):
            raise LocalCoverError(22, "视频不存在, videoId = " + ("null" if video_id is None else video_id))
        self._owned(video_id, user_id)
        video = self.store.video(video_id)
        if video is None:
            raise RuntimeError("Video disappeared after local cover authorization")
        whitespace = ("\t\n\v\f\r\x1c\x1d\x1e\x1f \u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006"
                      "\u2008\u2009\u200a\u2028\u2029\u205f\u3000")
        if extension is None or not any(char not in whitespace for char in extension):
            extension = "jpg"
        file = {"_id": self.ids.long_id("f_"), "uploaderId": video.get("uploaderId"), "videoId": video_id,
                "fileType": "COVER", "videoType": video.get("videoType"), "extension": extension,
                "createTime": self.clock(), "updateTime": self.clock(), "deleted": False,
                "fileStatus": "CREATED", "provider": "ALIYUN_OSS", "hasLink": False}
        self.store.save_file(file)
        cover = {"_id": self.ids.long_id("c_"), "userId": video.get("uploaderId"), "videoId": video_id,
                 "fileId": file["_id"], "provider": "LOCAL_COVER", "status": "CREATED",
                 "extension": extension, "createTime": self.clock()}
        month = video["createTime"].astimezone(self.local_timezone).strftime("%Y%m")
        key = (f"videos/{video.get('uploaderId')}/{month}/{video_id}/cover/{cover['_id']}/"
               f"{file['_id']}.{extension}")
        cover.update(key=key, accessUrl=self.access_base_url + key)
        self.store.save_cover(cover)
        file["key"] = key
        self.store.save_file(file)
        credentials = self.uploads.upload_credentials(key)
        if credentials is None:
            raise LocalCoverError(34)
        return CreatedLocalCover(cover["_id"], key, credentials)

    def finish(self, user_id: str, cover_id: str) -> None:
        cover = self.store.cover(cover_id)
        if cover is None:
            raise LocalCoverError(41, "封面不存在: " + cover_id)
        self._owned(cover.get("videoId"), user_id)
        cover.update(status="READY", finishTime=self.clock())
        self.store.save_cover(cover)
        file = self.store.file(cover.get("fileId"))
        if file is not None:
            file["fileStatus"] = "READY"
            self.store.save_file(file)
        video = self.store.video(cover.get("videoId"))
        if video is None:
            raise RuntimeError("Video disappeared after local cover completion")
        video["coverId"] = cover_id
        self.store.save_video(video)
