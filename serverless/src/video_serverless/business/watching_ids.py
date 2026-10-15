"""Each request creates a fresh persistent viewing identity, as in the Java service."""

from dataclasses import dataclass
from datetime import datetime
from typing import Callable

from video_serverless.ports.watching_ids import WatchingIdStore


@dataclass(frozen=True)
class WatchingIds:
    store: WatchingIdStore
    clock: Callable[[], datetime]

    def client(self, ip: str, user_agent: str | None) -> str:
        return self.store.create_client(self.clock(), ip, user_agent)

    def session(self, ip: str, user_agent: str | None) -> str:
        return self.store.create_session(self.clock(), ip, user_agent)
