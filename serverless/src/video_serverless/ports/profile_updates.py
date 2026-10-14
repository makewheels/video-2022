"""Only the legacy profile update capability, independent of database/HTTP types."""

from dataclasses import dataclass
from datetime import datetime
from typing import Protocol


@dataclass(frozen=True)
class ProfileUpdateResult:
    error: str | None = None


class ProfileWrites(Protocol):
    def update_profile(self, user_id: str, nickname: str | None, bio: str | None, updated_at: datetime) -> None: ...
