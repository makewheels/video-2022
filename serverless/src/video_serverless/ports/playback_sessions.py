"""Portable session snapshots and already-bound command values."""

from dataclasses import dataclass
from datetime import datetime
from typing import Protocol


@dataclass(frozen=True)
class PlaybackSession:
    id: str | None = None
    watch_id: str | None = None
    video_id: str | None = None
    user_id: str | None = None
    client_id: str | None = None
    session_id: str | None = None
    started_at: datetime | None = None
    ended_at: datetime | None = None
    duration_ms: int | None = None
    max_progress_ms: int | None = None
    current_progress_ms: int | None = None
    resolution: str | None = None
    exit_type: str | None = None
    heartbeat_count: int | None = None
    created_at: datetime | None = None
    updated_at: datetime | None = None


@dataclass(frozen=True)
class StartPlayback:
    watch_id: str | None = None
    video_id: str | None = None
    client_id: str | None = None
    session_id: str | None = None


@dataclass(frozen=True)
class PlaybackUpdate:
    id: str | None = None
    current_time_ms: int | None = None
    duration_ms: int | None = None
    resolution: str | None = None
    is_playing: bool | None = None
    exit_type: str | None = None


class PlaybackSessionStore(Protocol):
    def find(self, session_id: str | None) -> PlaybackSession | None: ...

    def save(self, session: PlaybackSession) -> str: ...
