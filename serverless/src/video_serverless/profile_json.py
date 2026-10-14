"""Uninstalled legacy profile DTO decoding for UTF-8 JSON; no web framework dependency."""

import json
from dataclasses import dataclass


class ProfileBindingError(ValueError):
    """Fixed binding diagnostic, without body contents or parser excerpts."""


class _Number(str):
    pass


class _Object(list):
    pass


@dataclass(frozen=True)
class ProfileInput:
    nickname: str | None
    bio: str | None


def _invalid_constant(value):
    raise ProfileBindingError("Profile JSON binding failed")


def _string(value):
    if value is None or isinstance(value, str):
        return None if value is None else str(value)
    if isinstance(value, bool):
        return "true" if value else "false"
    raise ProfileBindingError("Profile JSON binding failed")


def decode_profile(body: bytes) -> ProfileInput:
    try:
        text = body.decode("utf-8").lstrip(" \t\r\n")
        decoder = json.JSONDecoder(parse_int=_Number, parse_float=_Number,
                                   parse_constant=_invalid_constant, object_pairs_hook=_Object)
        value, end = decoder.raw_decode(text)
        # The actual legacy HTTP pipeline rejects both a second root and trailing garbage.
        if text[end:].strip(" \t\r\n"):
            raise ProfileBindingError("Profile JSON binding failed")
        if not isinstance(value, _Object):
            raise ProfileBindingError("Profile JSON binding failed")
        fields = {"nickname": None, "bio": None}
        for name, item in value:
            if name in fields:
                # Validate every occurrence: an earlier container is not hidden by a later duplicate.
                fields[name] = _string(item)
        return ProfileInput(**fields)
    except (UnicodeError, ValueError, RecursionError):
        raise ProfileBindingError("Profile JSON binding failed") from None
