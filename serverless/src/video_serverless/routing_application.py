"""Verified legacy OPTIONS and route-scoped client CORS; no global permissive CORS policy."""

from dataclasses import dataclass
from urllib.parse import urlsplit

from video_serverless.http import Response
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.request_cycle import CycleResult, RequestCycle

_ALL = "GET, HEAD, POST, PUT, DELETE, OPTIONS, TRACE, PATCH"
_VARY = "Origin, Access-Control-Request-Method, Access-Control-Request-Headers"


def _cross_origin(request):
    origin = request.first_header("Origin")
    if origin is None:
        return False
    def endpoint(value):
        parsed = urlsplit(value)
        return parsed.scheme.lower(), parsed.hostname, parsed.port or (443 if parsed.scheme == "https" else 80)
    try:
        return endpoint(origin) != endpoint(request.url)
    except ValueError:
        return True


@dataclass(frozen=True)
class RoutingApplication:
    methods: dict[str, str | None]
    protected: frozenset[str]
    cycle: RequestCycle

    def options(self, request: RequestMetadata) -> CycleResult:
        def handle(metadata, identity):
            wanted = metadata.first_header("Access-Control-Request-Method")
            preflight = _cross_origin(metadata) and wanted is not None
            if preflight:
                headers = {"Allow": _ALL}
                if metadata.path == "/client/requestClientId" and wanted == "GET":
                    headers.update({"Vary": _VARY, "Access-Control-Allow-Origin": "*",
                                    "Access-Control-Allow-Methods": "GET", "Access-Control-Max-Age": "1800"})
                    names = metadata.first_header("Access-Control-Request-Headers")
                    if names:
                        headers["Access-Control-Allow-Headers"] = names
            else:
                method = self.methods[metadata.path]
                allowed = "GET,HEAD,POST,PUT,PATCH,DELETE,OPTIONS" if method is None else (
                    "GET,HEAD,OPTIONS" if method == "GET" else method + ",OPTIONS")
                headers = {"Allow": allowed, "Accept-Patch": ""}
            return Response(200, b"", headers)
        result = self.cycle.run(request, handle, protected=request.path in self.protected)
        # FrameworkServlet adds its default Allow even when an interceptor rejected the request.
        if "Allow" not in result.response.headers:
            response = result.response
            return CycleResult(Response(response.status, response.body, {**response.headers, "Allow": _ALL}),
                               result.journal_status)
        return result

    def client_cors(self, request: RequestMetadata, result: CycleResult) -> CycleResult:
        if (request.path == "/client/requestClientId" and request.method in {"GET", "HEAD"}
                and _cross_origin(request)):
            response = result.response
            return CycleResult(Response(response.status, response.body,
                                        {**response.headers, "Vary": _VARY, "Access-Control-Allow-Origin": "*"}),
                               result.journal_status)
        return result
