"""History log/page values; no database or HTTP types in the business boundary."""

from dataclasses import dataclass
from datetime import datetime
from typing import Protocol


@dataclass(frozen=True)
class HistoryRow:
    video_id: str | None
    watched_at: datetime | None


@dataclass(frozen=True)
class HistoryVideo:
    id: str | None
    title: str | None
    cover_id: str | None


@dataclass(frozen=True)
class HistoryItem:
    video_id: str | None
    title: str | None
    cover_url: str | None
    watched_at: datetime | None


@dataclass(frozen=True)
class HistoryPage:
    items: tuple[HistoryItem, ...]
    total: int
    page: int
    page_size: int


class HistoryStore(Protocol):
    def page(self, viewer_id: str | None, skip: int, limit: int) -> tuple[HistoryRow, ...]: ...

    def count(self, viewer_id: str | None) -> int: ...

    def clear(self, viewer_id: str | None) -> None: ...

    def videos(self, ids: tuple[str | None, ...]) -> tuple[HistoryVideo, ...]: ...


class HistoryCoverUrls(Protocol):
    def signed(self, cover_id: str) -> str | None: ...
