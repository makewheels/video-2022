"""Legacy seven-field access claims and signature capability, with ordinary values only."""

from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class AccessClaim:
    video_id: str | None
    client_id: str | None
    session_id: str | None
    resolution: str | None
    file_id: str | None
    timestamp: str | None
    nonce: str | None


class AccessSignatures(Protocol):
    def sign(self, claim: AccessClaim) -> str: ...

    def verify(self, claim: AccessClaim, signature: str | None) -> bool: ...

    def timestamp_valid(self, timestamp: str | None) -> bool: ...
