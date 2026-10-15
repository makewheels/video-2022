"""Ordinary watch event values and storage/IP capabilities; no BSON or request globals."""

from dataclasses import dataclass
from datetime import datetime
from typing import Protocol


@dataclass(frozen=True)
class WatchVisit:
    video_id: str | None
    client_id: str | None
    session_id: str | None
    viewer_id: str | None
    ip: str
    user_agent: str | None


@dataclass(frozen=True)
class WatchVideo:
    id: str
    title: str | None
    status: str | None


@dataclass(frozen=True)
class WatchEvent:
    visit: WatchVisit
    video_status: str | None
    created_at: datetime
    ip_info: dict | None


class WatchLogStore(Protocol):
    def video_exists(self, video_id: str | None) -> bool: ...

    def duplicate(self, video_id: str | None, session_id: str | None, requested_status: str) -> bool: ...

    def video(self, video_id: str | None) -> WatchVideo | None: ...

    def increment(self, video_id: str | None) -> None: ...

    def save(self, event: WatchEvent) -> None: ...


class IpLocations(Protocol):
    def lookup(self, ip: str) -> dict | None: ...
