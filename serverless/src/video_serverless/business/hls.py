"""Legacy child playlist rewriting, including its line and MAP parsing semantics."""

from dataclasses import dataclass

from video_serverless.business.playback_authorization import PlaybackAuthorization, PlaybackContext
from video_serverless.ports.hls import HlsReads
from video_serverless.ports.playback_signatures import AccessClaim


class HlsDenied(Exception):
    def __init__(self, code, detail=None):
        self.code = code
        self.detail = detail
        super().__init__(detail)


def _trim(value):
    return value.strip("".join(chr(number) for number in range(33)))


def _lines(content):
    lines = content.split("\n")
    if content:
        while lines and not lines[-1]:
            lines.pop()
    return lines


def init_segment_uri(content):
    for original in _lines(content):
        line = _trim(original)
        if not line.startswith("#EXT-X-MAP:") or "URI=" not in line:
            continue
        rest = _trim(line.split("URI=", 1)[1])
        if len(rest) >= 2 and rest[0] == '"' and rest[-1] == '"':
            rest = rest[1:-1]
        else:
            rest = rest.split(",", 1)[0]
        return rest or None
    return None


def belongs_to_video(video, transcode):
    return video is not None and (
        video.id == transcode.video_id or (
            video.has_link and video.link_video_id == transcode.video_id
            and video.transcode_ids is not None and transcode.id in video.transcode_ids
        )
    )


def _java(value):
    return "null" if value is None else str(value)


@dataclass(frozen=True)
class ChildPlaylists:
    reads: HlsReads
    authorization: PlaybackAuthorization
    internal_base_url: str

    def get(self, context: PlaybackContext, transcode_id: str, resolution: str, user_id: str | None,
            timestamp=None, nonce=None, signature=None) -> str:
        transcode = self.reads.transcode(transcode_id)
        video = self.reads.video(context.video_id)
        if transcode is None or not belongs_to_video(video, transcode) or resolution != transcode.resolution:
            raise HlsDenied(35, "播放任务不属于当前视频或档位")
        denied = self.authorization.authorize(video, context, user_id, timestamp, nonce, signature)
        if denied is not None:
            raise HlsDenied(denied, "该视频为私密视频" if denied == 35 else None)
        files = {file.filename: file for file in self.reads.files(transcode.file_ids)}
        lines = _lines(transcode.content)
        for index, original in enumerate(lines):
            line = _trim(original)
            if line.startswith("#"):
                if line.startswith("#EXT-X-MAP:"):
                    uri = init_segment_uri(transcode.content)
                    file = files.get(uri) if uri is not None else None
                    if file is not None:
                        signed = self._access_url(context, transcode, file)
                        lines[index] = line.replace('URI="' + uri + '"', 'URI="' + signed + '"')
                continue
            file = files.get(line)
            if file is not None:
                lines[index] = self._access_url(context, transcode, file)
        return "\n".join(lines)

    def _access_url(self, context, transcode, file):
        if transcode.video_id != file.video_id or transcode.id != file.transcode_id:
            raise HlsDenied(35, "播放分片不属于当前视频")
        timestamp = str(self.authorization.clock_ms())
        nonce = self.authorization.nonce()
        claim = AccessClaim(context.video_id, context.client_id, context.session_id,
                            transcode.resolution, file.id, timestamp, nonce)
        pairs = (("resolution", transcode.resolution), ("tsIndex", file.index), ("fileType", file.file_type),
                 ("videoId", context.video_id), ("clientId", context.client_id), ("sessionId", context.session_id),
                 ("fileId", file.id), ("timestamp", timestamp), ("nonce", nonce),
                 ("sign", self.authorization.signatures.sign(claim)))
        return self.internal_base_url + "/file/access?" + "&".join(key + "=" + _java(value) for key, value in pairs)
