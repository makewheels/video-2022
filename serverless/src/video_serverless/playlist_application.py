"""Explicit HLS HTTP capability sharing identity, exception and journal handling."""

from dataclasses import dataclass

from video_serverless.business.hls import ChildPlaylists, HlsDenied
from video_serverless.business.hls_master import MasterPlaylists
from video_serverless.business.playback_authorization import PlaybackContext
from video_serverless.http import Response
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.request_cycle import CycleResult, RequestCycle
from video_serverless.user_application import UserBindingError, UserRequestError

MASTER = "/watchController/getMultivariantPlaylist.m3u8"
CHILD = "/watchController/getM3u8Content.m3u8"
PLAYLIST_ROUTES = {MASTER, CHILD}


def _response(content, accept):
    # String responses remain raw strings even when the caller requests application/json.
    candidates = []
    for item in (accept or "*/*").split(","):
        parts = [part.strip() for part in item.split(";")]
        media = parts[0].lower()
        parameters = [part for part in parts[1:] if not part.lower().startswith("q=")]
        quality = next((float(part[2:]) for part in parts[1:] if part.lower().startswith("q=")), 1.0)
        candidates.append((quality, "*" not in media, media, parameters))
    _, _, media, parameters = max(enumerate(candidates), key=lambda item: (item[1][0], item[1][1], -item[0]))[1]
    if "*" in media:
        media = "text/plain" if media in {"*/*", "text/*"} else "application/octet-stream"
    charset = next((part.split("=", 1)[1].strip('"') for part in parameters
                    if part.lower().startswith("charset=")), "UTF-8")
    if not any(part.lower().startswith("charset=") for part in parameters) and not (
        media == "application/json" or media.endswith("+json") or media == "application/octet-stream"
    ):
        parameters.append("charset=" + charset)
    content_type = media + "".join(";" + part for part in parameters)
    return Response(200, content.encode(charset, errors="replace"), {"Content-Type": content_type})


@dataclass(frozen=True)
class PlaylistApplication:
    master: MasterPlaylists
    child: ChildPlaylists
    cycle: RequestCycle

    def dispatch(self, request: RequestMetadata, body: bytes = b"") -> CycleResult:
        if request.path not in PLAYLIST_ROUTES:
            return CycleResult(Response.json({"message": "Playlist route not found"}, 404), "not_started")

        def handle(metadata, identity):
            if metadata.method not in {"GET", "HEAD"}:
                raise UserBindingError("Playlist request binding failed")
            context = PlaybackContext(*(metadata.first_parameter(name)
                                        for name in ("videoId", "clientId", "sessionId")))
            grant = tuple(metadata.first_parameter(name) for name in ("playbackTime", "playbackNonce", "playbackSign"))
            try:
                if metadata.path == MASTER:
                    content = self.master.get(context, identity.user_id, *grant)
                else:
                    transcode_id = metadata.first_parameter("transcodeId")
                    resolution = metadata.first_parameter("resolution")
                    if transcode_id is None or resolution is None:
                        raise UserBindingError("Playlist request binding failed")
                    content = self.child.get(context, transcode_id, resolution, identity.user_id, *grant)
            except HlsDenied as error:
                raise UserRequestError(error.code, "文件访问签名无效" if error.code == 35 else "视频不存在",
                                       error.detail) from error
            return _response(content, metadata.first_header("Accept"))

        return self.cycle.run(request, handle)
