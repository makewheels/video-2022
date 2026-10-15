"""Bound legacy heartbeat values, independent of JSON, Mongo and HTTP frameworks."""

from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal
from typing import Protocol


@dataclass(frozen=True)
class Heartbeat:
    id: str | None = None
    video_id: str | None = None
    client_id: str | None = None
    session_id: str | None = None
    viewer_id: str | None = None
    video_status: str | None = None
    player_provider: str | None = None
    client_time: datetime | None = None
    created_at: datetime | None = None
    type: str | None = None
    event: str | None = None
    player_time: int | None = None
    player_status: str | None = None
    player_volume: Decimal | None = None


class HeartbeatStore(Protocol):
    def save(self, heartbeat: Heartbeat) -> None: ...
