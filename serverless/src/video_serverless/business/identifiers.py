"""Legacy long IDs and Snowflake string layout, using shared sequence capabilities."""

from dataclasses import dataclass
from datetime import datetime
from typing import Callable

from video_serverless.ports.identifiers import IdSequences

SNOWFLAKE_EPOCH = 1288834974657


def snowflake_string(timestamp_ms: int, worker: int, datacenter: int, sequence: int) -> str:
    if not (0 <= worker <= 31 and 0 <= datacenter <= 31 and 0 <= sequence <= 4095):
        raise ValueError("Invalid Snowflake slot")
    elapsed = timestamp_ms - SNOWFLAKE_EPOCH
    if not 0 <= elapsed < 2**41:
        raise ValueError("Snowflake timestamp is outside the positive signed long range")
    return str((elapsed << 22) | (datacenter << 17) | (worker << 12) | sequence)


@dataclass(frozen=True)
class Identifiers:
    sequences: IdSequences
    local_clock: Callable[[], datetime]
    utc_clock_ms: Callable[[], int]
    random_three_digits: Callable[[], str]
    worker: int
    datacenter: int

    def long_id(self, prefix: str = "") -> str:
        # Java LocalDateTime.now().toInstant(UTC) treats local wall time as UTC.
        # Keep that existing behavior; deployment must supply the old JVM's local timezone.
        local = self.local_clock().replace(tzinfo=None)
        elapsed = local - datetime(2023, 1, 1)
        milliseconds = elapsed.days * 86_400_000 + elapsed.seconds * 1000 + elapsed.microseconds // 1000
        unit = milliseconds // 4000 if milliseconds >= 0 else -((-milliseconds) // 4000)
        sequence = self.sequences.next_long_sequence(unit)
        return prefix + str(unit) + f"{sequence:05d}" + self.random_three_digits()

    def user_id(self) -> str:
        return self.long_id("u_")

    def token(self) -> str:
        now = self.utc_clock_ms()
        # Validate configured node/time before consuming a shared slot.
        snowflake_string(now, self.worker, self.datacenter, 0)
        slot = self.sequences.reserve_token_slot(now)
        if slot.timestamp_ms - now >= 2000:
            raise RuntimeError("Clock moved backwards beyond the legacy Snowflake tolerance")
        return snowflake_string(slot.timestamp_ms, self.worker, self.datacenter, slot.sequence)
