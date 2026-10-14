"""Same read-only prototype application for Handler and FastAPI comparisons."""

import hmac
import os
from threading import Lock
from time import time
from uuid import uuid4

from video_serverless.http import Request, Response

_instance = uuid4().hex
_invocations = 0
_lock = Lock()


def dispatch(request: Request) -> Response:
    if request.path == "/healthCheck" and request.method == "GET":
        # Existing Java endpoint's text form. No database access on this path.
        return Response.text(f"ok {int(time() * 1000)}")
    if request.path not in {"/_prototype/empty", "/_prototype/mongo-read"}:
        return Response.json({"message": "Prototype route not found"}, 404)
    secret = os.environ.get("PROTOTYPE_TOKEN", "")
    if not secret or not hmac.compare_digest(request.header("x-prototype-token").encode(), secret.encode()):
        return Response.json({"message": "Forbidden"}, 403)
    if request.method != "GET":
        return Response(405, b"", {"Allow": "GET"})

    global _invocations
    with _lock:
        _invocations += 1
        ordinal = _invocations

    if request.path == "/_prototype/mongo-read":
        from video_serverless.mongo import read_probe

        try:
            payload = read_probe()
        except Exception:
            # Driver errors can contain connection/user details: don't return them.
            return Response.json({"message": "Mongo probe unavailable"}, 503)
    else:
        payload = {"ok": True}
    response = Response.json(payload)
    return Response(response.status, response.body, {
        **response.headers,
        "X-Prototype-Instance": _instance,
        "X-Prototype-Invocation": str(ordinal),
        "Cache-Control": "no-store",
    })
