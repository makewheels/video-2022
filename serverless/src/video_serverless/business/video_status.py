"""Keep the old uploader check, read sequence and exact READY comparison."""

from dataclasses import dataclass

from video_serverless.ports.video_status import VideoStatusReads, VideoStatusSnapshot


class VideoStatusError(RuntimeError):
    def __init__(self, code, detail):
        super().__init__(detail)
        self.code = code
        self.detail = detail


@dataclass(frozen=True)
class VideoStatuses:
    reads: VideoStatusReads

    def get(self, user_id: str, video_id: str) -> VideoStatusSnapshot:
        # Controller checks existence, then CheckService repeats it before user/owner lookups.
        for _ in range(2):
            if not self.reads.video_exists(video_id):
                raise VideoStatusError(22, "视频不存在, videoId = " + video_id)
        if not self.reads.user_exists(user_id):
            raise VideoStatusError(14, "用户不存在, userId = " + ("null" if user_id is None else user_id))
        video = self.reads.find(video_id)
        if video is None or video.uploader_id is None:
            raise RuntimeError("Video uploader disappeared during authorization")
        if video.uploader_id != user_id:
            raise VideoStatusError(23, "视频不属于user, videoId = " + video_id
                                   + ", ownerId = " + video.uploader_id + ", userId = "
                                   + ("null" if user_id is None else user_id))
        result = self.reads.find(video_id)
        if result is None:
            raise RuntimeError("Video disappeared after authorization")
        return result
