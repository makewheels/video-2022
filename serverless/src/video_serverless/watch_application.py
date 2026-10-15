"""Explicit watch-info and optional HLS HTTP composition."""

from dataclasses import dataclass

from video_serverless.business.watch_info import WatchInfos
from video_serverless.http import Response
from video_serverless.playlist_application import PLAYLIST_ROUTES, PlaylistApplication
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.request_cycle import CycleResult, RequestCycle
from video_serverless.user_application import UserBindingError, UserRequestError


@dataclass(frozen=True)
class WatchApplication:
    watches: WatchInfos
    cycle: RequestCycle
    playlists: PlaylistApplication | None = None

    def dispatch(self, request: RequestMetadata, body: bytes = b"") -> CycleResult:
        if self.playlists is not None and request.path in PLAYLIST_ROUTES:
            return self.playlists.dispatch(request, body)
        if request.path != "/watchController/getWatchInfo":
            return CycleResult(Response.json({"message": "Watch route not found"}, 404), "not_started")

        def handle(metadata, identity):
            watch_id = metadata.first_parameter("watchId")
            if metadata.method != "GET" or watch_id is None:
                raise UserBindingError("Watch request binding failed")
            if not self.watches.reads.exists(watch_id):
                raise UserRequestError(22, "视频不存在", "视频watchId不存在, watchId = " + watch_id)
            # Legacy RequestUtil.getContext only reads query parameters, not client/session headers.
            result = self.watches.get(watch_id, metadata.first_parameter("clientId"),
                                       metadata.first_parameter("sessionId"), identity.user_id)
            data = None if result.info is None else {
                "videoId": result.info.video_id, "coverUrl": result.info.cover_url,
                "multivariantPlaylistUrl": result.info.playlist_url, "videoStatus": result.info.video_status,
                "progressInMillis": result.info.progress_in_millis,
            }
            response = Response.json({"code": result.error_code or 0,
                                      "message": result.error_message or "success", "data": data})
            return Response(200, response.body, {"Content-Type": "application/json"})

        return self.cycle.run(request, handle)
