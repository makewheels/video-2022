"""Login persistence capabilities; existing user values stay provider neutral."""

from dataclasses import dataclass
from datetime import datetime
from typing import Protocol

from video_serverless.ports.users import UserSnapshot


@dataclass(frozen=True)
class LoginResult:
    user: UserSnapshot | None = None
    error_code: int | None = None


class LoginStore(Protocol):
    def code_for_phone(self, phone: str) -> str | None: ...

    def remove_codes(self, phone: str) -> None: ...

    def user_for_phone(self, phone: str) -> UserSnapshot | None: ...

    def save_user(self, user: UserSnapshot) -> None: ...

    def ensure_wallet(self, user_id: str, created_at: datetime) -> None: ...
