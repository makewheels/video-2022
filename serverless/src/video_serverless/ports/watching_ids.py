"""Persistence needed to allocate legacy client and session identities."""

from datetime import datetime
from typing import Protocol


class WatchingIdStore(Protocol):
    def create_client(self, created_at: datetime, ip: str, user_agent: str | None) -> str: ...

    def create_session(self, created_at: datetime, ip: str, user_agent: str | None) -> str: ...
