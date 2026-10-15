"""Video/cover/progress reads required by the existing watch-info response."""

from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class WatchVideo:
    id: str
    uploader_id: str | None
    visibility: str | None
    status: str | None
    cover_id: str | None


@dataclass(frozen=True)
class SavedProgress:
    milliseconds: int | None


@dataclass(frozen=True)
class WatchInfo:
    video_id: str
    cover_url: str | None
    playlist_url: str
    video_status: str | None
    progress_in_millis: int | None


@dataclass(frozen=True)
class WatchInfoResult:
    info: WatchInfo | None = None
    error_code: int | None = None
    error_message: str | None = None


class WatchReads(Protocol):
    def exists(self, watch_id: str) -> bool: ...

    def video(self, watch_id: str) -> WatchVideo | None: ...

    def cover_key(self, cover_id: str | None) -> str | None: ...

    def progress(self, video_id: str, viewer_id: str | None, client_id: str) -> SavedProgress | None: ...
