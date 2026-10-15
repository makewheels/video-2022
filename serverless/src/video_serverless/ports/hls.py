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
    provider: str | None = None
    current_provider: str | None = None
    status: str | None = None
    fallback_count: int | None = None
    max_bitrate: int | None = None
    average_bitrate: int | None = None
    width: int | None = None
    height: int | None = None
    actual_width: int | None = None
    actual_height: int | None = None
    codecs: str | None = None
    frame_rate: str | None = None
    dynamic_range: str | None = None


@dataclass(frozen=True)
class HlsAudio:
    track_count: int | None
    tracks: tuple[tuple[str | None, int | None], ...] | None


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

    def transcodes(self, transcode_ids: tuple[str, ...]) -> list[HlsTranscode]: ...

    def audio(self, video_id: str) -> HlsAudio | None: ...
