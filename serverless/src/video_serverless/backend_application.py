"""Explicit composition of implemented domains; no routes are installed at import time."""

from dataclasses import dataclass

from video_serverless.comment_application import COMMENT_READ_METHODS, CommentReadApplication
from video_serverless.comment_write_application import COMMENT_WRITE_METHODS, CommentWriteApplication
from video_serverless.file_upload_application import FILE_UPLOAD_ROUTES, FileUploadApplication
from video_serverless.local_cover_application import LOCAL_COVER_METHODS, LocalCoverApplication
from video_serverless.local_transcode_application import LOCAL_TRANSCODE_METHODS, LocalTranscodeApplication
from video_serverless.playlist_application import PLAYLIST_ROUTES
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.public_video_application import PublicVideoApplication
from video_serverless.request_cycle import CycleResult
from video_serverless.routing_application import RoutingApplication
from video_serverless.search_application import SearchApplication
from video_serverless.subscription_application import SUBSCRIPTION_READ_METHODS, SubscriptionReadApplication
from video_serverless.upload_application import UploadApplication
from video_serverless.user_application import UserApplication
from video_serverless.video_reaction_application import VIDEO_REACTION_METHODS, VideoReactionApplication
from video_serverless.viewing_application import VIEWING_ROUTES, ViewingApplication
from video_serverless.watch_application import WatchApplication


@dataclass(frozen=True)
class BackendApplication:
    users: UserApplication
    public_videos: PublicVideoApplication
    search: SearchApplication
    watch: WatchApplication | None = None
    viewing: ViewingApplication | None = None
    routing: RoutingApplication | None = None
    upload: UploadApplication | None = None
    file_uploads: FileUploadApplication | None = None
    local_covers: LocalCoverApplication | None = None
    local_transcodes: LocalTranscodeApplication | None = None
    comments: CommentReadApplication | None = None
    comment_writes: CommentWriteApplication | None = None
    video_reactions: VideoReactionApplication | None = None
    subscriptions: SubscriptionReadApplication | None = None

    def dispatch(self, request: RequestMetadata, body: bytes = b"") -> CycleResult:
        if self.routing is not None and request.method == "OPTIONS" and request.path in self.routing.methods:
            return self.routing.options(request)
        if self.subscriptions is not None and request.path in SUBSCRIPTION_READ_METHODS:
            return self.subscriptions.dispatch(request, body)
        if self.video_reactions is not None and request.path in VIDEO_REACTION_METHODS:
            return self.video_reactions.dispatch(request, body)
        if self.comments is not None and request.path in COMMENT_READ_METHODS:
            return self.comments.dispatch(request, body)
        if self.comment_writes is not None and request.path in COMMENT_WRITE_METHODS:
            return self.comment_writes.dispatch(request, body)
        if self.local_covers is not None and request.path in LOCAL_COVER_METHODS:
            return self.local_covers.dispatch(request, body)
        if self.local_transcodes is not None and request.path in LOCAL_TRANSCODE_METHODS:
            return self.local_transcodes.dispatch(request, body)
        if self.upload is not None and request.path in {"/video/create", "/video/getVideoStatus"}:
            return self.upload.dispatch(request, body)
        if self.file_uploads is not None and request.path in FILE_UPLOAD_ROUTES:
            return self.file_uploads.dispatch(request, body)
        if self.viewing is not None and request.path in VIEWING_ROUTES:
            return self.viewing.dispatch(request, body)
        if self.watch is not None and request.path in PLAYLIST_ROUTES | {"/watchController/getWatchInfo"}:
            return self.watch.dispatch(request, body)
        if request.path == "/video/getPublicVideoList":
            return self.public_videos.dispatch(request, body)
        if request.path in {"/search", "/category/list"}:
            return self.search.dispatch(request, body)
        result = self.users.dispatch(request, body)
        return result if self.routing is None else self.routing.client_cors(request, result)
