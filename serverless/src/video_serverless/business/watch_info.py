"""Existing watch-info composition and context defaults, without request globals."""

from dataclasses import dataclass
from typing import Callable

from video_serverless.business.playback_authorization import PlaybackAuthorization, PlaybackContext
from video_serverless.ports.users import AvatarUrls
from video_serverless.ports.watch_info import WatchInfo, WatchInfoResult, WatchReads


def _blank(value):
    return value is None or all(char.isspace() and char not in "\x85\u00a0\u2007\u202f" for char in value)


@dataclass(frozen=True)
class WatchInfos:
    reads: WatchReads
    urls: AvatarUrls
    authorization: PlaybackAuthorization
    internal_base_url: str
    nano_id: Callable[[], str]

    def get(self, watch_id: str, client_id: str | None, session_id: str | None, user_id: str | None) -> WatchInfoResult:
        video = self.reads.video(watch_id)
        if video is None:
            return WatchInfoResult(error_code=22, error_message="视频不存在")
        if video.visibility == "PRIVATE" and (user_id is None or user_id != video.uploader_id):
            return WatchInfoResult(error_code=1, error_message="该视频为私密视频")
        context = PlaybackContext(video.id, self.nano_id() if _blank(client_id) else client_id,
                                  self.nano_id() if _blank(session_id) else session_id)
        key = self.reads.cover_key(video.cover_id)
        cover_url = None if key is None else self.urls.sign(key, 7200)
        url = self.internal_base_url + "/watchController/getMultivariantPlaylist.m3u8?videoId=" + video.id
        url += "&clientId=" + context.client_id + "&sessionId=" + context.session_id
        playlist_url = self.authorization.sign_url(url, context)
        progress = self.reads.progress(video.id, user_id, context.client_id)
        return WatchInfoResult(WatchInfo(video.id, cover_url, playlist_url, video.status,
                                         0 if progress is None else progress.milliseconds))
