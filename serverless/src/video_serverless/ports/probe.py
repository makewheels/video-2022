"""Read-only diagnostic capability; no database query or driver types."""

from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class ProbeReading:
    found_public_video: bool
    client_setup_ms: float
    read_ms: float


class PublicVideoProbe(Protocol):
    def read_public_video(self) -> ProbeReading: ...
