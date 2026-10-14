"""Portable HTTP values; provider wire protocols live in adapters."""

import json
from dataclasses import dataclass, field
from typing import Any


@dataclass(frozen=True)
class Request:
    method: str
    path: str
    headers: dict[str, str] = field(default_factory=dict)
    query: dict[str, str] = field(default_factory=dict)
    body: bytes = b""

    def header(self, name: str) -> str:
        return self.headers.get(name.lower(), "")


@dataclass(frozen=True)
class Response:
    status: int
    body: bytes
    headers: dict[str, str] = field(default_factory=dict)

    @classmethod
    def text(cls, body: str, status: int = 200) -> "Response":
        return cls(status, body.encode("utf-8"), {"Content-Type": "text/plain; charset=utf-8"})

    @classmethod
    def json(cls, body: Any, status: int = 200) -> "Response":
        content = json.dumps(body, ensure_ascii=False, separators=(",", ":"), allow_nan=False)
        return cls(status, content.encode("utf-8"), {"Content-Type": "application/json; charset=utf-8"})
