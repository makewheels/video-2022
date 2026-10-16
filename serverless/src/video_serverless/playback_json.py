"""Separate Spring DTO and Fastjson exit binding for the legacy playback endpoints."""

import json
from decimal import Decimal

from video_serverless.body_text import json_text
from video_serverless.fastjson_input import decode_exit_object
from video_serverless.heartbeat_json import _long
from video_serverless.ports.playback_sessions import PlaybackUpdate, StartPlayback
from video_serverless.profile_json import _invalid_constant, _Number, _Object, _string

_START = {"watchId": "watch_id", "videoId": "video_id", "clientId": "client_id", "sessionId": "session_id"}
_STRINGS = {"playbackSessionId": "id", "resolution": "resolution"}
_LONGS = {"currentTimeMs": "current_time_ms", "totalPlayDurationMs": "duration_ms"}


class PlaybackBindingError(ValueError):
    pass


def _spring_object(body, content_type):
    text = json_text(body, content_type).lstrip(" \t\r\n")
    decoder = json.JSONDecoder(parse_int=_Number, parse_float=_Number,
                               parse_constant=_invalid_constant, object_pairs_hook=_Object)
    value, end = decoder.raw_decode(text)
    if not isinstance(value, _Object) or text[end:].strip(" \t\r\n"):
        raise PlaybackBindingError("Playback JSON binding failed")
    return value


def _boolean(value):
    if value is None or isinstance(value, bool):
        return value
    if isinstance(value, _Number) and not any(item in value for item in ".eE"):
        return int(value) != 0
    if isinstance(value, str):
        value = value.strip()
        if value in {"", "null"}:
            return None
        if value in {"true", "True", "TRUE", "false", "False", "FALSE"}:
            return value.lower() == "true"
    raise PlaybackBindingError("Playback boolean binding failed")


def _fast_json_text(value):
    if isinstance(value, _Number):
        return str(Decimal(value)) if any(item in value for item in ".eE") else str(int(value))
    if isinstance(value, _Object):
        # Generic Fastjson values are serialized when bound to a String field.
        fields = dict(value)
        return "{" + ",".join(json.dumps(name, ensure_ascii=False) + ":" + _fast_json_text(item)
                              for name, item in fields.items()) + "}"
    if isinstance(value, list):
        return "[" + ",".join(_fast_json_text(item) for item in value) + "]"
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def _fast_string(value):
    if isinstance(value, (_Number, list)):
        return _fast_json_text(value)
    return _string(value)


def decode_playback(body: bytes, route: str, content_type: str | None = None):
    try:
        pairs = decode_exit_object(body, content_type) if route == "exit" else _spring_object(body, content_type)
        fields = {}
        names = {key.replace("_", "").replace("-", "").lower(): key
                 for key in (*_STRINGS, *_LONGS, "exitType")}
        for name, value in pairs:
            if route == "start":
                if name in _START:
                    fields[_START[name]] = _string(value)
                continue
            if route == "exit":
                # Fastjson's verified smart-match differs from Spring's case-sensitive names.
                name = names.get(name.replace("_", "").replace("-", "").lower(), name)
            if name in _STRINGS:
                fields[_STRINGS[name]] = _fast_string(value) if route == "exit" else _string(value)
            elif name in _LONGS:
                if route == "exit" and isinstance(value, bool):
                    value = _Number(str(int(value)))
                fields[_LONGS[name]] = _long(value)
            elif name == "exitType" and route == "exit":
                fields["exit_type"] = _fast_string(value)
            elif name == "isPlaying" and route == "heartbeat":
                fields["is_playing"] = _boolean(value)
        return StartPlayback(**fields) if route == "start" else PlaybackUpdate(**fields)
    except (ValueError, UnicodeError, OverflowError, ArithmeticError, RecursionError):
        raise PlaybackBindingError("Playback JSON binding failed") from None
