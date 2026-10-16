"""Explicit routing between implemented viewing capabilities; never wraps a second request cycle."""

from dataclasses import dataclass

from video_serverless.heartbeat_application import HEARTBEAT_ROUTE, HeartbeatApplication
from video_serverless.history_progress_application import HISTORY_PROGRESS_ROUTES, HistoryProgressApplication
from video_serverless.http import Response
from video_serverless.playback_application import PLAYBACK_ROUTES, PlaybackApplication
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.request_cycle import CycleResult
from video_serverless.watch_log_application import WATCH_LOG_ROUTE, WatchLogApplication

VIEWING_ROUTES = HISTORY_PROGRESS_ROUTES | PLAYBACK_ROUTES | {HEARTBEAT_ROUTE, WATCH_LOG_ROUTE}


@dataclass(frozen=True)
class ViewingApplication:
    history: HistoryProgressApplication
    heartbeats: HeartbeatApplication
    playback: PlaybackApplication
    watch_logs: WatchLogApplication | None = None

    def dispatch(self, request: RequestMetadata, body: bytes = b"") -> CycleResult:
        if request.path in HISTORY_PROGRESS_ROUTES:
            return self.history.dispatch(request, body)
        if request.path in PLAYBACK_ROUTES:
            return self.playback.dispatch(request, body)
        if request.path == HEARTBEAT_ROUTE:
            return self.heartbeats.dispatch(request, body)
        if request.path == WATCH_LOG_ROUTE and self.watch_logs is not None:
            return self.watch_logs.dispatch(request, body)
        return CycleResult(Response.json({"message": "Viewing route not found"}, 404), "not_started")
