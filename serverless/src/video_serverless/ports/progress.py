"""Ordinary legacy progress values; heartbeat persistence and HTTP binding are separate."""

from dataclasses import dataclass
from datetime import datetime
from typing import Protocol


@dataclass(frozen=True)
class ProgressHeartbeat:
    video_id: str | None = None
    viewer_id: str | None = None
    client_id: str | None = None
    session_id: str | None = None
    player_time: int | None = None
    player_status: str | None = None


@dataclass(frozen=True)
class ProgressRecord:
    id: str | None
    video_id: str | None
    viewer_id: str | None
    client_id: str | None
    session_id: str | None
    milliseconds: int | None
    created_at: datetime | None
    updated_at: datetime | None


class ProgressStore(Protocol):
    def find(self, video_id: str | None, viewer_id: str | None, client_id: str | None) -> ProgressRecord | None: ...

    def save(self, progress: ProgressRecord) -> None: ...
