"""Public listing values and read capabilities; no Mongo documents or cursors."""

from dataclasses import dataclass
from datetime import datetime
from typing import Protocol


@dataclass(frozen=True)
class PublicVideo:
    id: str
    uploader_id: str | None
    title: str | None
    description: str | None
    status: str | None
    visibility: str | None
    tags: tuple[str | None, ...] | None
    category: str | None
    create_time: datetime | None
    cover_id: str | None
    watch_count: int | None
    watch_id: str | None
    watch_url: str | None
    short_url: str | None
    duration: int | None
    youtube_publish_time: datetime | None


class PublicVideoReads(Protocol):
    def page(self, skip: int, limit: int, keyword: str | None) -> tuple[PublicVideo, ...]: ...

    def count(self, keyword: str | None) -> int: ...


class CoverUrls(Protocol):
    def signed(self, cover_ids: tuple[str, ...]) -> dict[str, str]: ...
