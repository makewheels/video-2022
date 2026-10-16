"""Explicit playback-session HTTP composition; no production entrypoint registration."""

from dataclasses import dataclass

from video_serverless.business.playback_sessions import PlaybackSessions
from video_serverless.http import Response
from video_serverless.playback_json import PlaybackBindingError, decode_playback
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.request_cycle import CycleResult, RequestCycle
from video_serverless.video_responses import video_result

PLAYBACK_ROUTES = {"/playback/start", "/playback/heartbeat", "/playback/exit"}


@dataclass(frozen=True)
class PlaybackApplication:
    sessions: PlaybackSessions
    cycle: RequestCycle

    def dispatch(self, request: RequestMetadata, body: bytes = b"") -> CycleResult:
        if request.path not in PLAYBACK_ROUTES:
            return CycleResult(Response.json({"message": "Playback route not found"}, 404), "not_started")
        route = request.path.rsplit("/", 1)[1]
        media = (request.first_header("Content-Type") or "").split(";", 1)[0].strip().lower()
        # Spring rejects method/exit consumes mismatches before interceptor identity/journaling.
        if request.method != "POST" or (route == "exit" and media not in {"application/json", "text/plain"}):
            return CycleResult(self.cycle.error_response(PlaybackBindingError("Playback handler mapping failed")),
                               "not_started")

        def handle(metadata, identity):
            json_media = media == "application/json" or (media.startswith("application/") and media.endswith("+json"))
            if not (json_media or (route == "exit" and media == "text/plain")):
                raise PlaybackBindingError("Playback method/media binding failed")
            command = decode_playback(body, route, metadata.first_header("Content-Type"))
            if route == "start":
                return video_result({"playbackSessionId": self.sessions.start(command, identity.user_id)})
            if route == "heartbeat":
                self.sessions.heartbeat(command)
            else:
                self.sessions.exit(command)
            return video_result(None)

        return self.cycle.run(request, handle)
