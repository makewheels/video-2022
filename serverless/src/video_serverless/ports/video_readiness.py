"""Ordinary persisted lane state and a narrow video-status write capability."""

from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class LaneState:
    provider: str | None
    current_provider: str | None
    status: str | None


class ReadinessStore(Protocol):
    def lanes(self, transcode_ids: tuple[str, ...]) -> tuple[LaneState, ...]: ...
    def update_status(self, video_id: str, status: str) -> None: ...
