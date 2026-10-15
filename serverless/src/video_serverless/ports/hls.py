"""Persisted HLS input values, with no storage or request dependencies."""

from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class HlsVideo:
    id: str
    uploader_id: str | None
    visibility: str | None
    has_link: bool = False
    link_video_id: str | None = None
    transcode_ids: tuple[str, ...] | None = None


@dataclass(frozen=True)
class HlsTranscode:
    id: str
    video_id: str
    resolution: str | None
    file_ids: tuple[str, ...] | None
    content: str


@dataclass(frozen=True)
class HlsFile:
    id: str
    video_id: str | None
    transcode_id: str | None
    filename: str | None
    index: int | None
    file_type: str | None


class HlsReads(Protocol):
    def video(self, video_id: str | None) -> HlsVideo | None: ...

    def transcode(self, transcode_id: str) -> HlsTranscode | None: ...

    def files(self, file_ids: tuple[str, ...] | None) -> list[HlsFile]: ...
