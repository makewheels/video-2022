"""Private playback authorization independent of HTTP globals, cloud SDKs and persistence."""

from dataclasses import dataclass
from typing import Callable

from video_serverless.ports.playback_signatures import AccessClaim, AccessSignatures


@dataclass(frozen=True)
class PlaybackContext:
    video_id: str | None
    client_id: str | None
    session_id: str | None


@dataclass(frozen=True)
class PlaybackVideo:
    uploader_id: str | None
    visibility: str | None


@dataclass(frozen=True)
class PlaybackAuthorization:
    signatures: AccessSignatures
    clock_ms: Callable[[], int]
    nonce: Callable[[], str]

    def authorize(self, video: PlaybackVideo | None, context: PlaybackContext, user_id: str | None,
                  timestamp: str | None = None, nonce: str | None = None, signature: str | None = None) -> int | None:
        if video is None:
            return 22
        if video.visibility != "PRIVATE" or (video.uploader_id is not None and video.uploader_id == user_id):
            return None
        claim = AccessClaim(context.video_id, context.client_id, context.session_id, "", "playlist", timestamp, nonce)
        if self.signatures.timestamp_valid(timestamp) and self.signatures.verify(claim, signature):
            return None
        return 35

    def sign_url(self, url: str, context: PlaybackContext) -> str:
        timestamp = str(self.clock_ms())
        nonce = self.nonce()
        claim = AccessClaim(context.video_id, context.client_id, context.session_id, "", "playlist", timestamp, nonce)
        signature = self.signatures.sign(claim)
        return url + "&playbackTime=" + timestamp + "&playbackNonce=" + nonce + "&playbackSign=" + signature
