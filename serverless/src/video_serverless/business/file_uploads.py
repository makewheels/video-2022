"""Legacy file ownership, credentials and completion; no MD5 or task submission."""

from dataclasses import dataclass

from video_serverless.ports.file_uploads import FileCredentials, UploadFileStore, UploadObjects


class UploadFileError(RuntimeError):
    def __init__(self, code, detail=None):
        super().__init__(*(() if detail is None else (detail,)))
        self.code = code
        self.detail = detail


@dataclass(frozen=True)
class FileUploads:
    files: UploadFileStore
    objects: UploadObjects

    def _owned(self, user_id, file_id):
        if not self.files.exists(file_id):
            raise UploadFileError(31, "文件不存在, fileId = " + file_id)
        if user_id is None:
            raise UploadFileError(15, "用户UserHolder不存在")
        if self.files.owner(file_id) != user_id:
            raise UploadFileError(33, "文件和用户不匹配, user = " + user_id + ", fileId = " + file_id)
        # Keep the post-check read separate, like the old service; disappearance is a generic failure.
        file = self.files.find(file_id)
        if file is None:
            raise RuntimeError("File disappeared after upload authorization")
        return file

    def credentials(self, user_id: str, file_id: str) -> FileCredentials:
        file = self._owned(user_id, file_id)
        credentials = self.objects.upload_credentials(file.get("key"))
        if credentials is None:
            raise UploadFileError(34)
        return FileCredentials(credentials, file.get("provider"))

    def finish(self, user_id: str, file_id: str) -> None:
        file = self._owned(user_id, file_id)
        metadata = self.objects.metadata(file.get("key"))
        completed = file | {"size": metadata.size, "etag": metadata.etag,
                            "uploadTime": metadata.modified_at, "fileStatus": "READY"}
        self.files.save(completed)
