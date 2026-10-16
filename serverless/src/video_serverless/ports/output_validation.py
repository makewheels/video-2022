"""Portable output declaration and read-only object capabilities."""

from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class OutputObject:
    key: str | None
    size: int


@dataclass(frozen=True)
class OutputManifest:
    playlist_key: str | None
    objects: tuple[OutputObject, ...] | None = None


class OutputObjects(Protocol):
    def read_text(self, key: str) -> str | None: ...
    def size(self, key: str) -> int | None: ...
