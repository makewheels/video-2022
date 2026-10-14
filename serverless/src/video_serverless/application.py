"""Provider-neutral HTTP routing for the isolated prototype use cases."""

import hmac
from threading import Lock
from uuid import uuid4

from video_serverless.business.prototype import Prototype
from video_serverless.http import Request, Response


class Diagnostics:
    """Process-local measurements only; never persistent business state."""

    def __init__(self):
        self._instance = uuid4().hex
        self._invocations = 0
        self._lock = Lock()

    def headers(self) -> dict[str, str]:
        with self._lock:
            self._invocations += 1
            ordinal = self._invocations
        return {
            "X-Prototype-Instance": self._instance,
            "X-Prototype-Invocation": str(ordinal),
            "Cache-Control": "no-store",
        }


class Application:
    def __init__(self, prototype: Prototype, prototype_token: str):
        self._prototype = prototype
        self._prototype_token = prototype_token
        self._diagnostics = Diagnostics()

    def dispatch(self, request: Request) -> Response:
        if request.path == "/healthCheck" and request.method == "GET":
            return Response.text(f"ok {self._prototype.health()}")
        if request.path not in {"/_prototype/empty", "/_prototype/mongo-read"}:
            return Response.json({"message": "Prototype route not found"}, 404)
        if not self._prototype_token or not hmac.compare_digest(
            request.header("x-prototype-token").encode(), self._prototype_token.encode(),
        ):
            return Response.json({"message": "Forbidden"}, 403)
        if request.method != "GET":
            return Response(405, b"", {"Allow": "GET"})

        headers = self._diagnostics.headers()
        if request.path == "/_prototype/mongo-read":
            try:
                reading = self._prototype.read_public_video()
                payload = {
                    "foundPublicVideo": reading.found_public_video,
                    "clientSetupMs": reading.client_setup_ms,
                    "readMs": reading.read_ms,
                }
            except Exception:
                # Preserve the existing contract, without leaking provider errors.
                return Response.json({"message": "Mongo probe unavailable"}, 503)
        else:
            payload = {"ok": self._prototype.empty()}
        response = Response.json(payload)
        return Response(response.status, response.body, {**response.headers, **headers})
