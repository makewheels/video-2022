"""Explicit OSS registration reads; signed HTTP transport is supplied, never created here."""

from datetime import UTC, datetime

from video_serverless.adapters.local_output_json import LocalOutputSidecar
from video_serverless.adapters.oss_output_objects import OssOutputObjects
from video_serverless.ports.local_output_registration import StoredOutput


def _date(value):
    return None if value is None else datetime.fromtimestamp(value, UTC)


class OssOutputRegistration(OssOutputObjects):
    def __init__(self, bucket, http_get, warning):
        super().__init__(bucket)
        self._http_get, self._warning = http_get, warning

    def read_sidecar(self, key):
        from oss2.exceptions import OssError

        try:
            content = self.read_text(key)
        except (OSError, OssError) as error:
            self._warning("读取对象失败 key = " + key + ": " + type(error).__name__)
            return None
        return None if content is None else LocalOutputSidecar(content)

    def signed_playlist(self, key):
        return self._http_get(self._bucket.sign_url("GET", key, 600))

    def metadata(self, key):
        result = self._bucket.get_object(key)
        if result is None:
            return None
        try:
            return StoredOutput(key, result.content_length, result.etag,
                                result.headers.get("x-oss-storage-class", "Standard"), _date(result.last_modified))
        finally:
            result.close()

    def list_outputs(self, prefix):
        outputs, token = [], ""
        while True:
            page = self._bucket.list_objects_v2(prefix=prefix, continuation_token=token, max_keys=1000)
            outputs.extend(StoredOutput(item.key, item.size, item.etag, item.storage_class, _date(item.last_modified))
                           for item in page.object_list)
            if not page.is_truncated:
                return tuple(outputs)
            token = page.next_continuation_token
