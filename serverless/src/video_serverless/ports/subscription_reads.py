"""Ordinary values for the existing user's subscription reads."""

from typing import Protocol


class SubscriptionReads(Protocol):
    def subscribed(self, user_id: str | None, channel_user_id: str) -> bool: ...
    def channels(self, user_id: str | None, skip: int, limit: int) -> tuple[str | None, ...]: ...
