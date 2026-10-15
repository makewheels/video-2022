"""Verified standard JSON subset of old IP responses/cache, retaining decimal values."""

import json
from decimal import Decimal


class IpMappingError(ValueError):
    pass


def _invalid_constant(value):
    raise ValueError()


def decode(text):
    if text is None or not text.strip():
        return None
    try:
        value = json.loads(text, parse_float=Decimal,
                           parse_constant=_invalid_constant)
        if value is not None and not isinstance(value, dict):
            raise ValueError()
        return value
    except (ValueError, TypeError, RecursionError):
        raise IpMappingError("IP response/cache JSON shape is not verified") from None


def encode(value):
    # Fastjson's cache serialization drops map nulls but retains null array elements.
    if isinstance(value, dict):
        return "{" + ",".join(json.dumps(key, ensure_ascii=False) + ":" + encode(item)
                               for key, item in value.items() if item is not None) + "}"
    if isinstance(value, list):
        return "[" + ",".join(encode(item) for item in value) + "]"
    if isinstance(value, Decimal):
        if not value.is_finite():
            raise IpMappingError("IP decimal JSON value is not verified")
        return str(value)
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), allow_nan=False)
