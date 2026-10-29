"""Ordinary legacy reaction records and the ordered storage operations they require."""

from dataclasses import dataclass
from datetime import datetime
from typing import Protocol


@dataclass(frozen=True)
class VideoReaction:
    id: str | None
    video_id: str | None
    user_id: str | None
    reaction_type: str | None
    create_time: datetime | None
    update_time: datetime | None


class VideoReactionStore(Protocol):
    def video_exists(self, video_id: str) -> bool: ...
    def counts(self, video_id: str) -> tuple[int | None, int | None] | None: ...
    def find_reaction(self, video_id: str, user_id: str | None) -> VideoReaction | None: ...
    def save_reaction(self, reaction: VideoReaction) -> None: ...
    def remove_reaction(self, reaction_id: str) -> None: ...
    def increment_count(self, video_id: str, reaction_type: str, delta: int) -> None: ...
