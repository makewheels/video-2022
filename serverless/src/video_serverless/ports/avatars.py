"""Avatar persistence and object authorization values, without SDK types."""

from dataclasses import dataclass, field
from datetime import datetime
from typing import Protocol


@dataclass(frozen=True)
class AvatarFile:
    id: str
    uploader_id: str | None
    key: str | None
    file_type: str | None


@dataclass(frozen=True)
class UploadCredentials:
    bucket: str
    key: str
    endpoint: str
    access_key_id: str = field(repr=False)
    secret_key: str = field(repr=False)
    session_token: str = field(repr=False)
    expiration: str


@dataclass(frozen=True)
class AvatarResult:
    file_id: str | None = None
    key: str | None = None
    credentials: UploadCredentials | None = field(default=None, repr=False)
    error_code: int | None = None


class AvatarStore(Protocol):
    def create(self, file_id: str, uploader_id: str | None, key: str, created_at: datetime) -> None: ...

    def find(self, file_id: str) -> AvatarFile | None: ...

    def bind(self, user_id: str, file_id: str, updated_at: datetime) -> None: ...


class AvatarObjects(Protocol):
    def upload_credentials(self, key: str) -> UploadCredentials | None: ...

    def exists(self, key: str) -> bool: ...
