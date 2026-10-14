"""FC 3.0 HTTP event/response conversion; no FC envelope enters common HTTP models."""

import base64
import json
from typing import Any

from video_serverless.bootstrap import dispatch
from video_serverless.http import Request, Response


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


def to_fc(response: Response) -> dict[str, Any]:
    # Text decoding alone cannot identify content type. Binary media stays base64,
    # including byte sequences which happen to also be valid UTF-8.
    mime = response.headers.get("Content-Type", "").split(";", 1)[0].lower()
    textual = mime.startswith("text/") or mime in {
        "application/json", "application/vnd.apple.mpegurl", "application/x-mpegurl",
    }
    try:
        body = response.body.decode("utf-8") if textual else base64.b64encode(response.body).decode("ascii")
    except UnicodeDecodeError:
        textual = False
        body = base64.b64encode(response.body).decode("ascii")
    return {"statusCode": response.status, "headers": response.headers, "isBase64Encoded": not textual, "body": body}


def handler(event, context):
    return to_fc(dispatch(from_fc(event)))
