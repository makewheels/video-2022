"""Explicit object bucket adapter: text for manifests/playlists, headers only for media objects."""


class OssOutputObjects:
    def __init__(self, bucket):
        self._bucket = bucket

    def read_text(self, key):
        result = self._bucket.get_object(key)
        if result is None:
            return None
        try:
            # Java new String(bytes, UTF_8) replaces invalid byte sequences.
            return result.read().decode("utf-8", errors="replace")
        finally:
            result.close()

    def size(self, key):
        result = self._bucket.get_object(key)
        if result is None:
            return None
        try:
            return result.content_length or 0
        finally:
            # Never buffer a video segment just to verify its declared object size.
            result.close()
