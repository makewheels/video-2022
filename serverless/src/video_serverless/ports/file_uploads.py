"""File upload authorization and remote metadata capabilities, with ordinary values."""

from dataclasses import dataclass, field
from datetime import datetime
from typing import Protocol

from video_serverless.ports.avatars import UploadCredentials


@dataclass(frozen=True)
class UploadedObject:
    size: int
    etag: str | None
    modified_at: datetime | None


@dataclass(frozen=True)
class FileCredentials:
    credentials: UploadCredentials = field(repr=False)
    provider: str | None


class UploadFileStore(Protocol):
    def exists(self, file_id: str) -> bool: ...

    def owner(self, file_id: str) -> str | None: ...

    def find(self, file_id: str) -> dict | None: ...

    def save(self, document: dict) -> None: ...


class UploadObjects(Protocol):
    def upload_credentials(self, key: str) -> UploadCredentials | None: ...

    def metadata(self, key: str) -> UploadedObject: ...
