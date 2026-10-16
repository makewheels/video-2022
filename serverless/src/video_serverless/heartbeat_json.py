"""Legacy UTF-8 heartbeat binding, preserving numeric tokens and duplicate-field order."""

import json
import re
from datetime import UTC, datetime, timedelta
from decimal import Decimal, InvalidOperation
from email.utils import parsedate_to_datetime

from video_serverless.ports.heartbeat import Heartbeat
from video_serverless.profile_json import _invalid_constant, _Number, _Object, _string

_STRINGS = {"id": "id", "videoId": "video_id", "clientId": "client_id", "sessionId": "session_id",
            "viewerId": "viewer_id", "videoStatus": "video_status", "playerProvider": "player_provider",
            "type": "type", "event": "event", "playerStatus": "player_status"}
_INTEGER = re.compile(r"[+-]?[0-9]+\Z")
_DECIMAL = re.compile(r"[+-]?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[eE][+-]?[0-9]+)?\Z")


class HeartbeatBindingError(ValueError):
    """Fixed diagnostic; never include request body values."""


def _decimal(value):
    if value is None:
        return None
    if not isinstance(value, str):
        raise HeartbeatBindingError("Heartbeat number binding failed")
    text = value.strip()
    if not text or text == "null":
        return None
    if not _DECIMAL.fullmatch(text):
        raise HeartbeatBindingError("Heartbeat number binding failed")
    result = Decimal(text)
    if not result.is_finite():
        raise HeartbeatBindingError("Heartbeat number binding failed")
    return result


def _long(value):
    if value is None:
        return None
    if isinstance(value, _Number):
        result = Decimal(value)
        if not -(2**63) <= result < 2**63:
            raise HeartbeatBindingError("Heartbeat long binding failed")
        return int(result)
    if not isinstance(value, str):
        raise HeartbeatBindingError("Heartbeat long binding failed")
    text = value.strip()
    if not text or text == "null":
        return None
    if not _INTEGER.fullmatch(text):
        raise HeartbeatBindingError("Heartbeat long binding failed")
    result = int(text)
    if not -(2**63) <= result < 2**63:
        raise HeartbeatBindingError("Heartbeat long binding failed")
    return result


def _date(value):
    if value is None:
        return None
    if not isinstance(value, str):
        raise HeartbeatBindingError("Heartbeat date binding failed")
    text = value.strip()
    if not text or text == "null":
        return None
    if _INTEGER.fullmatch(text):
        return datetime(1970, 1, 1, tzinfo=UTC) + timedelta(milliseconds=int(text))
    if isinstance(value, _Number):
        raise HeartbeatBindingError("Heartbeat date binding failed")
    if "," in text:
        result = parsedate_to_datetime(text)
    else:
        try:
            result = datetime.fromisoformat(text)
        except ValueError:
            # Legacy StdDateFormat is lenient: 2024-02-30 becomes 2024-03-01.
            match = re.fullmatch(r"([0-9]{4})-([0-9]{2})-([0-9]{2})", text)
            if match is None:
                raise
            year, month, day = (int(part) for part in match.groups())
            result = datetime(year, month, 1, tzinfo=UTC) + timedelta(days=day - 1)
    return result.replace(tzinfo=UTC) if result.tzinfo is None else result.astimezone(UTC)


def decode_heartbeat(body: bytes) -> Heartbeat:
    try:
        text = body.decode("utf-8-sig").lstrip(" \t\r\n")
        decoder = json.JSONDecoder(parse_int=_Number, parse_float=_Number,
                                   parse_constant=_invalid_constant, object_pairs_hook=_Object)
        value, end = decoder.raw_decode(text)
        if not isinstance(value, _Object) or text[end:].strip(" \t\r\n"):
            raise HeartbeatBindingError("Heartbeat JSON binding failed")
        fields = {}
        for name, item in value:
            if name in _STRINGS:
                fields[_STRINGS[name]] = _string(item)
            elif name == "playerTime":
                fields["player_time"] = _long(item)
            elif name == "playerVolume":
                fields["player_volume"] = _decimal(item)
            elif name in {"clientTime", "createTime"}:
                fields["client_time" if name == "clientTime" else "created_at"] = _date(item)
        return Heartbeat(**fields)
    except (ValueError, UnicodeError, OverflowError, InvalidOperation, RecursionError):
        raise HeartbeatBindingError("Heartbeat JSON binding failed") from None
