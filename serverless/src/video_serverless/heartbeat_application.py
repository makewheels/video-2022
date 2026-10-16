"""Explicit legacy heartbeat HTTP composition; not registered in existing entrypoints."""

from dataclasses import dataclass

from video_serverless.business.heartbeat import Heartbeats
from video_serverless.heartbeat_json import HeartbeatBindingError, decode_heartbeat
from video_serverless.http import Response
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.request_cycle import CycleResult, RequestCycle
from video_serverless.video_responses import video_result

HEARTBEAT_ROUTE = "/heartbeat/add"


@dataclass(frozen=True)
class HeartbeatApplication:
    heartbeats: Heartbeats
    cycle: RequestCycle

    def dispatch(self, request: RequestMetadata, body: bytes = b"") -> CycleResult:
        if request.path != HEARTBEAT_ROUTE:
            return CycleResult(Response.json({"message": "Heartbeat route not found"}, 404), "not_started")

        def handle(metadata, identity):
            media = (metadata.first_header("Content-Type") or "").split(";", 1)[0].strip().lower()
            if media != "application/json" and not (media.startswith("application/") and media.endswith("+json")):
                raise HeartbeatBindingError("Heartbeat content type binding failed")
            # Legacy @RequestMapping has no GET/POST restriction. No new token/ownership check.
            self.heartbeats.add(decode_heartbeat(body, metadata.first_header("Content-Type")), identity.user)
            return video_result(None)

        return self.cycle.run(request, handle)
