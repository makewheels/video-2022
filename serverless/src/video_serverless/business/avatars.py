"""Legacy avatar lifecycle; caller preserves the existing authentication boundary."""

from dataclasses import dataclass
from datetime import datetime
from typing import Callable

from video_serverless.ports.avatars import AvatarFile, AvatarObjects, AvatarResult, AvatarStore


@dataclass(frozen=True)
class Avatars:
    files: AvatarStore
    objects: AvatarObjects
    file_id: Callable[[], str]
    clock: Callable[[], datetime]

    def create(self, user_id: str | None) -> AvatarResult:
        file_id = self.file_id()
        key = "avatar/" + ("null" if user_id is None else user_id) + "/" + file_id
        self.files.create(file_id, user_id, key, self.clock())
        return AvatarResult(file_id=file_id, key=key)

    def _owned(self, user_id: str | None, file_id: str) -> AvatarFile | AvatarResult:
        file = self.files.find(file_id)
        if file is None or file.file_type != "AVATAR":
            return AvatarResult(error_code=31)
        if user_id is None:
            # Legacy unprotected route dereferences a null user only after checking the file.
            raise RuntimeError("Legacy avatar operation requires a request user")
        if user_id != file.uploader_id:
            return AvatarResult(error_code=33)
        return file

    def credentials(self, user_id: str | None, file_id: str) -> AvatarResult:
        file = self._owned(user_id, file_id)
        if isinstance(file, AvatarResult):
            return file
        credentials = self.objects.upload_credentials(file.key)
        return (AvatarResult(error_code=34) if credentials is None
                else AvatarResult(file_id=file_id, credentials=credentials))

    def finish(self, user_id: str | None, file_id: str) -> AvatarResult:
        file = self._owned(user_id, file_id)
        if isinstance(file, AvatarResult):
            return file
        if not self.objects.exists(file.key):
            return AvatarResult(error_code=31)
        self.files.bind(user_id, file_id, self.clock())
        return AvatarResult()
