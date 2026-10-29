"""Legacy subscription status and the original list limit policy."""

from dataclasses import dataclass

from video_serverless.ports.subscription_reads import SubscriptionReads


@dataclass(frozen=True)
class Subscriptions:
    store: SubscriptionReads

    def status(self, user_id: str | None, channel_user_id: str) -> bool:
        return self.store.subscribed(user_id, channel_user_id)

    def mine(self, user_id: str | None, skip: int, limit: int) -> tuple[str | None, ...]:
        return self.store.channels(user_id, skip, min(limit, 50))
