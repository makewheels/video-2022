"""Synchronous request completion capability, expressed only as ordinary immutable values."""

from dataclasses import dataclass, field
from typing import Protocol

from video_serverless.ports.request_identity import RequestMetadata


@dataclass(frozen=True)
class RequestJournalEntry:
    metadata: RequestMetadata = field(repr=False)
    started_at_ms: int
    ended_at_ms: int
    http_status: int

    @property
    def time_cost_ms(self) -> int:
        return self.ended_at_ms - self.started_at_ms


class RequestJournal(Protocol):
    def save(self, entry: RequestJournalEntry) -> None: ...
