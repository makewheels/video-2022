"""Ordinary values for the uploader's lightweight video status poll."""

from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class VideoStatusSnapshot:
    id: str
    uploader_id: str | None
    status: str | None


class VideoStatusReads(Protocol):
    def video_exists(self, video_id: str) -> bool: ...

    def user_exists(self, user_id: str) -> bool: ...

    def find(self, video_id: str) -> VideoStatusSnapshot | None: ...
