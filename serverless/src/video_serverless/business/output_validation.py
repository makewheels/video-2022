"""Read-only legacy playlist/manifest/output cross-checks, shared by LOCAL and cloud results."""

from dataclasses import dataclass

from video_serverless.business.hls import _lines, _trim, init_segment_uri
from video_serverless.ports.output_validation import OutputManifest, OutputObjects

_FIRST = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-"
_ALL = _FIRST + "."
_WHITESPACE = ("\t\n\v\f\r\x1c\x1d\x1e\x1f \u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006"
               "\u2008\u2009\u200a\u2028\u2029\u205f\u3000")


def _safe_filename(value):
    return bool(value) and value[0] in _FIRST and all(char in _ALL for char in value) and ".." not in value


@dataclass(frozen=True)
class OutputValidation:
    objects: OutputObjects

    def _exists(self, key):
        try:
            size = self.objects.size(key)
            return size is not None and size > 0
        except Exception:
            return False

    def validate(self, playlist_key: str, manifest: OutputManifest) -> str | None:
        if manifest.playlist_key is None or manifest.playlist_key != playlist_key:
            actual = "null" if manifest.playlist_key is None else manifest.playlist_key
            return "manifest playlistKey 与任务不符: " + actual
        try:
            content = self.objects.read_text(playlist_key)
        except Exception:
            content = None
        if content is None or not any(char not in _WHITESPACE for char in content):
            return "playlist 不存在或为空: " + playlist_key
        if "#EXT-X-ENDLIST" not in content:
            return "playlist 缺少 ENDLIST"
        names = [_trim(line) for line in _lines(content) if _trim(line) and not _trim(line).startswith("#")]
        if not names:
            return "playlist 无分片"
        # Object-store keys in the migrated protocol are POSIX paths, never local filesystem paths.
        folder = playlist_key[:playlist_key.rfind("/") + 1]
        for name in names:
            if not _safe_filename(name):
                return "非法分片路径"
            if not self._exists(folder + name):
                return "分片缺失: " + name
        init = init_segment_uri(content)
        if init is not None and (not _safe_filename(init) or not self._exists(folder + init)):
            return "init segment 缺失: " + init
        if manifest.objects is not None:
            expected = {playlist_key} | {folder + name for name in names}
            if init is not None:
                expected.add(folder + init)
            listed = set()
            for item in manifest.objects:
                if item.key not in expected or item.key in listed:
                    return "manifest 对象越界或重复"
                listed.add(item.key)
                try:
                    size = self.objects.size(item.key)
                    if size is None or item.size <= 0 or size != item.size:
                        return "manifest 对象大小不符"
                except Exception:
                    return "manifest 对象读取失败"
            if listed != expected:
                return "manifest 缺少引用对象"
        return None
