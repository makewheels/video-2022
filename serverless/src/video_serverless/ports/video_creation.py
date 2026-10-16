"""Ordinary creation values and ordered persistence capabilities."""

from dataclasses import dataclass
from datetime import datetime
from typing import Protocol


@dataclass(frozen=True)
class CreateVideo:
    video_type: str | None = None
    raw_filename: str | None = None
    size: int | None = None
    transcode_mode: str | None = None


class CreationWrites(Protocol):
    def save_file(self, document: dict) -> None: ...

    def save_video(self, document: dict) -> None: ...


class CreationIds(Protocol):
    def long_id(self, prefix: str = "") -> str: ...

    def short_id(self) -> str: ...


@dataclass(frozen=True)
class CreatedVideo:
    video_id: str
    file_id: str
    watch_id: str
    watch_url: str


class CreationClock(Protocol):
    def __call__(self) -> datetime: ...
