"""Existing verification-code persistence, without provider types."""

from datetime import datetime
from typing import Protocol


class VerificationCodes(Protocol):
    def exists_for_phone(self, phone: str) -> bool: ...

    def save(self, phone: str, code: str, created_at: datetime) -> None: ...
