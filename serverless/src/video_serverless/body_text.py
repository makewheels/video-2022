"""Legacy JSON stream detection versus String converter decoding, verified with byte fixtures."""

import codecs
import json

_UNICODE = {"utf-8", "utf-16", "utf-16-le", "utf-16-be", "utf-32", "utf-32-le", "utf-32-be"}


def _charset(content_type):
    for parameter in (content_type or "").split(";")[1:]:
        name, separator, value = parameter.partition("=")
        if separator and name.strip().lower() == "charset":
            try:
                return codecs.lookup(value.strip().strip('"')).name
            except LookupError:
                raise ValueError("Body charset binding failed") from None
    return None


def json_text(body: bytes, content_type: str | None = None) -> str:
    charset = _charset(content_type)
    if charset is None or charset in _UNICODE:
        # Like the old Jackson InputStream path, byte/BOM detection is independent of Unicode labels.
        return body.decode(json.detect_encoding(body))
    # Non-Unicode declared charset uses the legacy Reader path.
    return body.decode(charset, errors="replace")


def string_text(body: bytes, content_type: str | None = None) -> str:
    # StringHttpMessageConverter does not auto-detect UTF16/32 and replaces malformed byte sequences.
    return body.decode(_charset(content_type) or "utf-8", errors="replace").lstrip("\ufeff")
