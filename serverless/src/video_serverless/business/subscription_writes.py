"""Legacy subscribe/unsubscribe order, direct errors and partial writes; no implicit replay."""

from dataclasses import dataclass
from datetime import datetime
from typing import Callable

from video_serverless.ports.subscription_writes import SubscriptionWritesStore


@dataclass(frozen=True)
class SubscriptionWrites:
    store: SubscriptionWritesStore
    clock: Callable[[], datetime]

    def subscribe(self, user_id: str | None, channel_user_id: str) -> str | None:
        if user_id is None:
            raise ValueError("Subscription creation requires the resolved legacy user ID")
        if user_id == channel_user_id:
            return "不能订阅自己"
        if not self.store.channel_exists(channel_user_id):
            return "频道不存在"
        if self.store.find_subscription(user_id, channel_user_id) is not None:
            return "已经订阅"
        self.store.create_subscription(user_id, channel_user_id, self.clock())
        self.store.increment_subscribers(channel_user_id, 1)
        return None

    def unsubscribe(self, user_id: str | None, channel_user_id: str) -> str | None:
        existing = self.store.find_subscription(user_id, channel_user_id)
        if existing is None:
            return "未订阅"
        self.store.remove_subscription(existing.id)
        self.store.increment_subscribers(channel_user_id, -1)
        return None
