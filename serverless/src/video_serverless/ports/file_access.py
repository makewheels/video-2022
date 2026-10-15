"""Storage capabilities for signed delivery, independent of providers and HTTP."""

from dataclasses import dataclass, field
from typing import Protocol


class FileRedirectKeys(Protocol):
    def key_for_file(self, file_id: str | None) -> str | None: ...


class FileRedirectUrls(Protocol):
    def sign(self, key: str, validity_seconds: int) -> str: ...


@dataclass(frozen=True)
class FileRedirectResult:
    url: str | None = field(default=None, repr=False)
    error_code: int | None = None
    detail: str | None = None
