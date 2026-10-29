"""Explicit legacy video reaction HTTP routes using the shared identity and journal lifecycle."""

from dataclasses import dataclass

from video_serverless.business.video_reactions import ReactionVideoMissing, VideoReactions
from video_serverless.http import Response
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.request_cycle import CycleResult, RequestCycle
from video_serverless.user_application import UserBindingError, UserRequestError

VIDEO_REACTION_METHODS = dict.fromkeys(("/videoLike/like", "/videoLike/dislike", "/videoLike/getStatus"), "GET")
VIDEO_REACTION_PROTECTED = frozenset({"/videoLike/like", "/videoLike/dislike"})


def _video_id(request: RequestMetadata) -> str:
    values = [value for name, value in request.parameters if name == "videoId"]
    if not values:
        raise UserBindingError("Required request parameter is missing")
    # Spring converts repeated String parameters into one comma-joined value.
    return ",".join(values)


@dataclass(frozen=True)
class VideoReactionApplication:
    reactions: VideoReactions
    cycle: RequestCycle

    def dispatch(self, request: RequestMetadata, body: bytes = b"") -> CycleResult:
        if request.path not in VIDEO_REACTION_METHODS:
            return CycleResult(Response.json({"message": "Video reaction route not found"}, 404), "not_started")
        if request.method not in {"GET", "HEAD"}:
            return CycleResult(self.cycle.error_response(UserBindingError("Request method is not supported")),
                               "not_started")

        def handle(metadata, identity):
            try:
                video_id = _video_id(metadata)
                data = None
                if metadata.path == "/videoLike/getStatus":
                    data = self.reactions.status(identity.user_id, video_id)
                else:
                    reaction_type = "LIKE" if metadata.path == "/videoLike/like" else "DISLIKE"
                    self.reactions.react(identity.user_id, video_id, reaction_type)
            except ReactionVideoMissing as error:
                raise UserRequestError(22, "视频不存在", str(error)) from None
            response = Response.json({"code": 0, "message": "success", "data": data})
            return Response(response.status, response.body, {"Content-Type": "application/json"})

        return self.cycle.run(request, handle, protected=request.path in VIDEO_REACTION_PROTECTED)
