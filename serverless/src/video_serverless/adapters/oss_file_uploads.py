"""Explicit SDK bucket and credential provider adapter; never configure or call cloud at import time."""

from datetime import UTC, datetime

from video_serverless.ports.file_uploads import UploadedObject


class OssUploadObjects:
    def __init__(self, credentials, bucket):
        self._credentials = credentials
        self._bucket = bucket

    def upload_credentials(self, key):
        return self._credentials.upload_credentials(key)

    def metadata(self, key):
        result = self._bucket.get_object(key)
        try:
            return UploadedObject(result.content_length or 0, result.etag,
                                  None if result.last_modified is None
                                  else datetime.fromtimestamp(result.last_modified, UTC))
        finally:
            # Only metadata is needed; do not buffer an entire uploaded video in a function.
            result.close()
