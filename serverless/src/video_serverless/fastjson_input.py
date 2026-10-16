"""Bounded ordinary parser for the verified legacy exit JSON dialect; no evaluation or type loading."""

import re

from video_serverless.body_text import string_text
from video_serverless.profile_json import _Number, _Object

_NUMBER = re.compile(r"[+-]?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[eE][+-]?[0-9]+)?")
_NAME = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*")
_ESCAPES = {"b": "\b", "f": "\f", "n": "\n", "r": "\r", "t": "\t", "v": "\v",
            "\\": "\\", "/": "/", '"': '"', "'": "'"}


class ExitJsonError(ValueError):
    pass


class _Parser:
    def __init__(self, text):
        self.text = text
        self.index = 0

    def space(self):
        while self.index < len(self.text):
            if self.text[self.index] in " \t\r\n\b\f":
                self.index += 1
            elif self.text.startswith("//", self.index):
                end = self.text.find("\n", self.index + 2)
                self.index = len(self.text) if end < 0 else end + 1
            elif self.text.startswith("/*", self.index):
                end = self.text.find("*/", self.index + 2)
                if end < 0:
                    raise ExitJsonError("Exit JSON binding failed")
                self.index = end + 2
            else:
                break

    def peek(self):
        self.space()
        return self.text[self.index:self.index + 1]

    def take(self, character):
        if self.peek() != character:
            raise ExitJsonError("Exit JSON binding failed")
        self.index += 1

    def string(self):
        quote = self.peek()
        self.index += 1
        parts = []
        while self.index < len(self.text):
            character = self.text[self.index]
            self.index += 1
            if character == quote:
                return "".join(parts)
            if character == "\\":
                escape = self.text[self.index:self.index + 1]
                self.index += 1
                if escape in _ESCAPES:
                    character = _ESCAPES[escape]
                elif escape in {"u", "x"}:
                    size = 4 if escape == "u" else 2
                    digits = self.text[self.index:self.index + size]
                    if len(digits) != size:
                        raise ExitJsonError("Exit JSON binding failed")
                    character = chr(int(digits, 16))
                    self.index += size
                else:
                    raise ExitJsonError("Exit JSON binding failed")
            parts.append(character)
        raise ExitJsonError("Exit JSON binding failed")

    def value(self):
        character = self.peek()
        if character in {"'", '"'}:
            return self.string()
        if character in {"{", "["}:
            return self.container(character == "{")
        for word, value in (("null", None), ("true", True), ("false", False)):
            if self.text.startswith(word, self.index):
                self.index += len(word)
                return value
        match = _NUMBER.match(self.text, self.index)
        if match is None:
            raise ExitJsonError("Exit JSON binding failed")
        self.index = match.end()
        return _Number(match.group())

    def container(self, object_value):
        self.index += 1
        close = "}" if object_value else "]"
        values = _Object() if object_value else []
        while True:
            # Legacy AllowArbitraryCommas accepts leading/repeated/trailing commas.
            while self.peek() == ",":
                self.index += 1
            if self.peek() == close:
                self.index += 1
                return values
            if object_value:
                if self.peek() in {"'", '"'}:
                    name = self.string()
                else:
                    match = _NAME.match(self.text, self.index)
                    if match is None:
                        raise ExitJsonError("Exit JSON binding failed")
                    name = match.group()
                    self.index = match.end()
                self.take(":")
                values.append((name, self.value()))
            else:
                values.append(self.value())
            if self.peek() not in {",", close}:
                raise ExitJsonError("Exit JSON binding failed")


def decode_exit_object(body: bytes, content_type: str | None = None):
    parser = _Parser(string_text(body, content_type))
    value = parser.value()
    if not isinstance(value, _Object) or parser.peek():
        raise ExitJsonError("Exit JSON binding failed")
    return value
