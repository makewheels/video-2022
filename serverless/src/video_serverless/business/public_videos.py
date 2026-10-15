"""Public page assembly without storage, HTTP or cloud dependencies."""

from dataclasses import dataclass
from datetime import datetime
from typing import Callable

from video_serverless.ports.public_videos import CoverUrls, PublicVideo, PublicVideoReads
from video_serverless.ports.users import UserReads


@dataclass(frozen=True)
class VideoCard:
    source: PublicVideo
    cover_url: str | None
    uploader_name: str
    uploader_avatar_url: str | None
    uploader_id: str | None
    create_time_string: str | None
    youtube_publish_time_string: str | None


@dataclass(frozen=True)
class PublicPage:
    videos: tuple[VideoCard, ...]
    total: int


@dataclass(frozen=True)
class PublicVideos:
    videos: PublicVideoReads
    covers: CoverUrls
    users: UserReads
    display_date: Callable[[datetime | None], str | None]

    def page(self, skip: int, limit: int, keyword: str | None) -> PublicPage:
        sources = self.videos.page(skip, limit, keyword)
        result = self.enrich(sources)
        # Preserve the separate count after page enrichment; no pretend atomic snapshot.
        return PublicPage(result, self.videos.count(keyword))

    def enrich(self, sources: tuple[PublicVideo, ...]) -> tuple[VideoCard, ...]:
        covers = self.covers.signed(tuple(video.cover_id for video in sources if video.cover_id is not None))
        uploaders = {user_id: self.users.find_by_id(user_id)
                     for user_id in dict.fromkeys(video.uploader_id for video in sources) if user_id is not None}
        result = []
        for video in sources:
            user = uploaders.get(video.uploader_id)
            name = "未知用户"
            avatar = None
            if user is not None:
                avatar = user.avatar_url
                if user.nickname:
                    name = user.nickname
                elif user.phone is not None and len(user.phone) >= 7:
                    name = user.phone[:3] + "****" + user.phone[-4:]
            result.append(VideoCard(video, covers.get(video.cover_id), name, avatar,
                                    video.uploader_id if user is None else user.id,
                                    self.display_date(video.create_time),
                                    self.display_date(video.youtube_publish_time)))
        return tuple(result)
