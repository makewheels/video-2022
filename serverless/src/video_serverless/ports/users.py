"""Only the user read capabilities and ordinary immutable values needed by the use cases."""

from dataclasses import dataclass
from datetime import datetime
from typing import Protocol


@dataclass(frozen=True)
class UserSnapshot:
    id: str
    phone: str | None = None
    register_channel: str | None = None
    create_time: datetime | None = None
    update_time: datetime | None = None
    token: str | None = None
    nickname: str | None = None
    avatar_file_id: str | None = None
    avatar_url: str | None = None
    banner_url: str | None = None
    bio: str | None = None
    subscriber_count: int | None = None
    video_count: int | None = None


class UserReads(Protocol):
    def find_by_token(self, token: str) -> UserSnapshot | None: ...

    def find_by_id(self, user_id: str) -> UserSnapshot | None: ...
