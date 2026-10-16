"""Explicit composition of implemented domains; no routes are installed at import time."""

from dataclasses import dataclass

from video_serverless.playlist_application import PLAYLIST_ROUTES
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.public_video_application import PublicVideoApplication
from video_serverless.request_cycle import CycleResult
from video_serverless.search_application import SearchApplication
from video_serverless.user_application import UserApplication
from video_serverless.viewing_application import VIEWING_ROUTES, ViewingApplication
from video_serverless.watch_application import WatchApplication


@dataclass(frozen=True)
class BackendApplication:
    users: UserApplication
    public_videos: PublicVideoApplication
    search: SearchApplication
    watch: WatchApplication | None = None
    viewing: ViewingApplication | None = None

    def dispatch(self, request: RequestMetadata, body: bytes = b"") -> CycleResult:
        if self.viewing is not None and request.path in VIEWING_ROUTES:
            return self.viewing.dispatch(request, body)
        if self.watch is not None and request.path in PLAYLIST_ROUTES | {"/watchController/getWatchInfo"}:
            return self.watch.dispatch(request, body)
        if request.path == "/video/getPublicVideoList":
            return self.public_videos.dispatch(request, body)
        if request.path in {"/search", "/category/list"}:
            return self.search.dispatch(request, body)
        return self.users.dispatch(request, body)
