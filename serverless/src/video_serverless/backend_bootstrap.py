"""Explicit isolated backend assembly; production environment and entrypoints stay separate."""

from datetime import UTC, datetime

from video_serverless.adapters.mongo_comment_reads import MongoCommentReads
from video_serverless.adapters.mongo_comment_writes import MongoCommentWrites
from video_serverless.adapters.mongo_cover_urls import MongoCoverUrls
from video_serverless.adapters.mongo_local_covers import MongoLocalCovers
from video_serverless.adapters.mongo_local_transcodes import MongoLocalTranscodes
from video_serverless.adapters.mongo_public_videos import MongoPublicVideos
from video_serverless.adapters.mongo_users import MongoUserReads
from video_serverless.adapters.mongo_video_search import MongoVideoSearch
from video_serverless.backend_application import BackendApplication
from video_serverless.business.comment_reads import Comments
from video_serverless.business.comment_writes import CommentWrites
from video_serverless.business.local_covers import LocalCovers
from video_serverless.business.local_transcodes import LocalTranscodes
from video_serverless.business.public_videos import PublicVideos
from video_serverless.business.video_search import VideoSearch
from video_serverless.comment_application import COMMENT_READ_METHODS, CommentReadApplication
from video_serverless.comment_write_application import COMMENT_WRITE_METHODS, CommentWriteApplication
from video_serverless.file_upload_application import FILE_UPLOAD_ROUTES
from video_serverless.local_cover_application import LOCAL_COVER_METHODS, LocalCoverApplication
from video_serverless.local_transcode_application import LOCAL_TRANSCODE_METHODS, LocalTranscodeApplication
from video_serverless.public_video_application import PublicVideoApplication
from video_serverless.routing_application import RoutingApplication
from video_serverless.search_application import SearchApplication
from video_serverless.upload_bootstrap import (
    build_file_upload_application,
    build_upload_application,
    build_upload_identifiers,
)
from video_serverless.user_application import _AVATAR_PATHS, _METHODS, _PROTECTED
from video_serverless.user_bootstrap import build_user_application
from video_serverless.viewing_bootstrap import build_viewing_application
from video_serverless.watch_bootstrap import build_watch_application


def build_backend_application(database_provider, urls, *, profile, local_clock, display_timezone,
                              worker, datacenter, avatar_objects=None,
                              playback_signature_secret=None, internal_base_url=None, ip_locations=None,
                              creation_base_url=None, upload_objects=None, local_cover_access_base_url=None,
                              local_transcode_registration=False, public_comment_reads=False, comment_writes=False):
    if local_transcode_registration and upload_objects is None:
        raise ValueError("Local transcode registration requires explicit object credentials")
    if local_cover_access_base_url is not None and (not local_cover_access_base_url or upload_objects is None):
        raise ValueError("Local cover registration requires explicit objects and access base URL")
    if (playback_signature_secret is None) != (internal_base_url is None):
        raise ValueError("Playback signature secret and internal base URL must be configured together")
    if playback_signature_secret is not None and (not playback_signature_secret or not internal_base_url):
        raise ValueError("Playback configuration must not be empty")
    users = build_user_application(database_provider, urls, profile=profile, local_clock=local_clock,
                                   worker=worker, datacenter=datacenter, avatar_objects=avatar_objects)

    def display_date(value):
        return None if value is None else value.astimezone(display_timezone).strftime("%Y-%m-%d %H:%M:%S")

    videos = PublicVideos(MongoPublicVideos(database_provider, lambda: datetime.now(UTC)),
                          MongoCoverUrls(database_provider, urls), MongoUserReads(database_provider), display_date)
    search = VideoSearch(MongoVideoSearch(database_provider, lambda: datetime.now(UTC)), videos)
    watch = None if playback_signature_secret is None else build_watch_application(
        database_provider, urls, users.cycle, signature_secret=playback_signature_secret,
        internal_base_url=internal_base_url,
    )
    viewing = build_viewing_application(database_provider, urls, users.cycle, ip_locations=ip_locations)
    methods = {**_METHODS, "/video/getPublicVideoList": "GET", "/search": "GET", "/category/list": "GET",
               "/progress/getProgress": "GET", "/watchHistory/getMyHistory": "GET", "/watchHistory/clear": "DELETE",
               "/heartbeat/add": None, "/playback/start": "POST", "/playback/heartbeat": "POST",
               "/playback/exit": "POST"}
    if users.avatars is None:
        for path in _AVATAR_PATHS:
            methods.pop(path)
    if viewing.watch_logs is not None:
        methods["/watchController/addWatchLog"] = "GET"
    if watch is not None:
        methods.update(dict.fromkeys(("/watchController/getWatchInfo", "/watchController/getMultivariantPlaylist.m3u8",
                                      "/watchController/getM3u8Content.m3u8"), "GET"))
    upload = None if creation_base_url is None else build_upload_application(
        database_provider, users.cycle, local_clock=local_clock, worker=worker, datacenter=datacenter,
        internal_base_url=creation_base_url, local_timezone=display_timezone,
    )
    protected = set(_PROTECTED)
    if upload is not None:
        methods["/video/create"] = "POST"
        methods["/video/getVideoStatus"] = "GET"
        protected.update({"/video/create", "/video/getVideoStatus"})
    file_uploads = None if upload_objects is None else build_file_upload_application(
        database_provider, users.cycle, upload_objects,
    )
    if file_uploads is not None:
        methods.update(dict.fromkeys(FILE_UPLOAD_ROUTES, "GET"))
        protected.update(FILE_UPLOAD_ROUTES)
    local_covers = None
    if local_cover_access_base_url is not None:
        local_covers = LocalCoverApplication(LocalCovers(
            MongoLocalCovers(database_provider), upload_objects,
            build_upload_identifiers(database_provider, local_clock, worker, datacenter),
            lambda: datetime.now(UTC), display_timezone, local_cover_access_base_url,
        ), users.cycle)
        methods.update(LOCAL_COVER_METHODS)
        protected.update(LOCAL_COVER_METHODS)
    local_transcodes = None
    if local_transcode_registration:
        local_transcodes = LocalTranscodeApplication(LocalTranscodes(
            MongoLocalTranscodes(database_provider), upload_objects, lambda: datetime.now(UTC), display_timezone,
        ), users.cycle)
        methods.update(LOCAL_TRANSCODE_METHODS)
        protected.update(LOCAL_TRANSCODE_METHODS)
    comments = None
    if public_comment_reads:
        comments = CommentReadApplication(Comments(MongoCommentReads(database_provider)), users.cycle)
        methods.update(COMMENT_READ_METHODS)
    writes = None
    if comment_writes:
        writes = CommentWriteApplication(CommentWrites(MongoCommentWrites(database_provider),
                                                       lambda: datetime.now(UTC)), users.cycle)
        methods.update(COMMENT_WRITE_METHODS)
        protected.update(COMMENT_WRITE_METHODS)
    routing = RoutingApplication(methods, frozenset(protected), users.cycle)
    return BackendApplication(users, PublicVideoApplication(videos, users.cycle),
                              SearchApplication(search, users.cycle), watch, viewing, routing, upload, file_uploads,
                              local_covers, local_transcodes, comments, writes)
