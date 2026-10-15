"""Existing IP cache values and storage/API capabilities, without provider types."""

from dataclasses import dataclass
from datetime import datetime
from typing import Protocol


@dataclass(frozen=True)
class IpCacheRecord:
    location_json: str | None


class IpCache(Protocol):
    def find(self, key: str) -> IpCacheRecord | None: ...

    def save(self, key: str, location_json: str, created_at: datetime) -> None: ...


class IpApi(Protocol):
    def lookup(self, ip: str) -> dict | None: ...
