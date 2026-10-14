"""FC 3.0 HTTP event adapter independent of any Web framework."""

import base64
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

    def fc(self) -> dict[str, Any]:
        # Text decoding alone cannot identify content type. Binary media stays base64,
        # including byte sequences which happen to also be valid UTF-8.
        mime = self.headers.get("Content-Type", "").split(";", 1)[0].lower()
        textual = mime.startswith("text/") or mime in {
            "application/json", "application/vnd.apple.mpegurl", "application/x-mpegurl",
        }
        try:
            body = self.body.decode("utf-8") if textual else base64.b64encode(self.body).decode("ascii")
        except UnicodeDecodeError:
            textual = False
            body = base64.b64encode(self.body).decode("ascii")
        return {"statusCode": self.status, "headers": self.headers, "isBase64Encoded": not textual, "body": body}


def from_fc(event: bytes | str | dict[str, Any]) -> Request:
    value = json.loads(event) if isinstance(event, (bytes, str)) else event
    http = value.get("requestContext", {}).get("http", {})
    encoded = value.get("body") or ""
    body = base64.b64decode(encoded, validate=True) if value.get("isBase64Encoded") else encoded.encode("utf-8")
    return Request(
        method=http.get("method", "GET").upper(),
        # rawPath is encoded; use FC's decoded path, avoiding double decoding.
        path=http.get("path", value.get("rawPath", "/")),
        headers={key.lower(): str(val) for key, val in (value.get("headers") or {}).items()},
        query={key: str(val) for key, val in (value.get("queryParameters") or {}).items()},
        body=body,
    )
