"""Shared sequence reservation; no database expressions cross this boundary."""

from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class TokenSlot:
    timestamp_ms: int
    sequence: int


class IdSequences(Protocol):
    def next_long_sequence(self, time_unit: int) -> int: ...

    def next_short_sequence(self, local_day: str) -> int: ...

    def reserve_token_slot(self, timestamp_ms: int) -> TokenSlot: ...
