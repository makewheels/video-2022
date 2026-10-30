"""Ordinary values for legacy subscription mutations and their independent counter writes."""

from dataclasses import dataclass
from datetime import datetime
from typing import Protocol


@dataclass(frozen=True)
class Subscription:
    id: str | None
    user_id: str | None
    channel_user_id: str | None
    create_time: datetime | None


class SubscriptionWritesStore(Protocol):
    def channel_exists(self, channel_user_id: str) -> bool: ...
    def find_subscription(self, user_id: str | None, channel_user_id: str) -> Subscription | None: ...
    def create_subscription(self, user_id: str, channel_user_id: str, created: datetime) -> None: ...
    def remove_subscription(self, subscription_id: str | None) -> None: ...
    def increment_subscribers(self, channel_user_id: str, delta: int) -> None: ...
