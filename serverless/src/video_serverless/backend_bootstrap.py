"""Explicit isolated backend assembly; production environment and entrypoints stay separate."""

from datetime import UTC, datetime

from video_serverless.adapters.mongo_cover_urls import MongoCoverUrls
from video_serverless.adapters.mongo_public_videos import MongoPublicVideos
from video_serverless.adapters.mongo_users import MongoUserReads
from video_serverless.adapters.mongo_video_search import MongoVideoSearch
from video_serverless.backend_application import BackendApplication
from video_serverless.business.public_videos import PublicVideos
from video_serverless.business.video_search import VideoSearch
from video_serverless.public_video_application import PublicVideoApplication
from video_serverless.search_application import SearchApplication
from video_serverless.user_bootstrap import build_user_application


def build_backend_application(database_provider, urls, *, profile, local_clock, display_timezone,
                              worker, datacenter, avatar_objects=None):
    users = build_user_application(database_provider, urls, profile=profile, local_clock=local_clock,
                                   worker=worker, datacenter=datacenter, avatar_objects=avatar_objects)

    def display_date(value):
        return None if value is None else value.astimezone(display_timezone).strftime("%Y-%m-%d %H:%M:%S")

    videos = PublicVideos(MongoPublicVideos(database_provider, lambda: datetime.now(UTC)),
                          MongoCoverUrls(database_provider, urls), MongoUserReads(database_provider), display_date)
    search = VideoSearch(MongoVideoSearch(database_provider, lambda: datetime.now(UTC)), videos)
    return BackendApplication(users, PublicVideoApplication(videos, users.cycle),
                              SearchApplication(search, users.cycle))
