"""Explicit composition of implemented domains; no routes are installed at import time."""

from dataclasses import dataclass

from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.public_video_application import PublicVideoApplication
from video_serverless.request_cycle import CycleResult
from video_serverless.user_application import UserApplication


@dataclass(frozen=True)
class BackendApplication:
    users: UserApplication
    public_videos: PublicVideoApplication

    def dispatch(self, request: RequestMetadata, body: bytes = b"") -> CycleResult:
        if request.path == "/video/getPublicVideoList":
            return self.public_videos.dispatch(request, body)
        return self.users.dispatch(request, body)
