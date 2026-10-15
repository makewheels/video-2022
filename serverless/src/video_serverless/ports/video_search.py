"""Video search capability, preserving separate page and count queries."""

from typing import Protocol

from video_serverless.ports.public_videos import PublicVideo


class VideoSearchReads(Protocol):
    def page(self, skip: int, limit: int, keyword: str | None, category: str | None) -> tuple[PublicVideo, ...]: ...

    def count(self, keyword: str | None, category: str | None) -> int: ...
