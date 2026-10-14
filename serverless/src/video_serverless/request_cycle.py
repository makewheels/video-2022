"""Portable, uninstalled request lifecycle; concrete routing and journal storage remain separate."""

from dataclasses import dataclass, field
from typing import Callable, Literal

from video_serverless.business.request_identity import IdentityResolver
from video_serverless.http import Response
from video_serverless.ports.request_identity import RequestMetadata, ResolvedIdentity
from video_serverless.ports.request_journal import RequestJournal, RequestJournalEntry


def unknown_error_response(error: Exception) -> Response:
    # This is the verified generic fallback; domain-specific exception mapping is still separate.
    response = Response.json({"code": 1, "message": "未知错误", "data": None})
    return Response(response.status, response.body, {"Content-Type": "application/json"})


def _unauthorized() -> Response:
    response = Response.json({"code": 401, "message": "请先登录"}, 401)
    return Response(response.status, response.body, {"Content-Type": "application/json;charset=UTF-8"})


@dataclass(frozen=True)
class CycleResult:
    response: Response = field(repr=False)
    journal_status: Literal["saved", "skipped", "failed", "not_started"]


@dataclass(frozen=True)
class RequestCycle:
    identities: IdentityResolver
    journal: RequestJournal
    clock_ms: Callable[[], int]
    error_response: Callable[[Exception], Response] = unknown_error_response

    def run(self, request: RequestMetadata, handler: Callable[[RequestMetadata, ResolvedIdentity], Response],
            *, protected: bool = False) -> CycleResult:
        try:
            identity = self.identities.resolve(request)
            # Legacy CheckToken performs a second lookup, without replacing PutToken's identity.
            if protected and self.identities.resolve(request).user is None:
                return CycleResult(_unauthorized(), "not_started")
        except Exception as error:
            return CycleResult(self.error_response(error), "not_started")

        started = self.clock_ms()
        try:
            response = handler(request, identity)
        except Exception as error:
            response = self.error_response(error)
        ended = self.clock_ms()
        if request.path in {"/file/access", "/heartbeat/add"}:
            return CycleResult(response, "skipped")
        entry = RequestJournalEntry(request, started, ended, response.status)
        try:
            # Finish synchronously before returning; no thread, retry or business replay.
            self.journal.save(entry)
        except Exception:
            # The caller sees a fixed diagnostic state, never raw credential-bearing exceptions.
            return CycleResult(response, "failed")
        return CycleResult(response, "saved")
