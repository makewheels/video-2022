"""Master playlist publication from successful, owned and populated transcodes."""

from dataclasses import dataclass
from decimal import ROUND_HALF_UP, Decimal, localcontext

from video_serverless.business.hls import HlsDenied, _java, belongs_to_video
from video_serverless.business.playback_authorization import PlaybackAuthorization, PlaybackContext
from video_serverless.business.watch_info import _blank
from video_serverless.ports.hls import HlsReads

MPS = "ALIYUN_MPS_TRANSCODE"
FUNCTIONS = {"ALIYUN_CLOUD_FUNCTION_TRANSCODE", "ALIYUN_CLOUD_FUNCTION_TRANSCODE_CPU",
             "ALIYUN_CLOUD_FUNCTION_TRANSCODE_GPU", "LOCAL_TRANSCODE"}


def successful(transcode):
    provider = transcode.current_provider if transcode.current_provider is not None else transcode.provider
    return (provider == MPS and transcode.status == "TranscodeSuccess"
            or provider in FUNCTIONS and transcode.status == "FINISHED")


def decimal_frame_rate(value):
    parts = value.split("/")
    while parts and not parts[-1]:
        parts.pop()
    for part in parts[:2]:
        if any(char.isspace() or char == "_" for char in part):
            raise ValueError("invalid decimal frame rate")
    numerator = Decimal(parts[0])
    denominator = Decimal(parts[1]) if len(parts) > 1 else Decimal(1)
    if not numerator.is_finite() or not denominator.is_finite():
        raise ValueError("invalid decimal frame rate")
    with localcontext() as context:
        context.prec = max(28, len(numerator.as_tuple().digits) + len(denominator.as_tuple().digits)
                           + abs(numerator.adjusted()) + abs(denominator.adjusted()) + 10)
        result = (numerator / denominator).quantize(Decimal("0.001"), rounding=ROUND_HALF_UP)
        return format(abs(result) if result.is_zero() else result, "f")


def media_attributes(transcode):
    result = ""
    width = transcode.actual_width if transcode.actual_width is not None else transcode.width
    height = transcode.actual_height if transcode.actual_height is not None else transcode.height
    if width is not None and height is not None:
        result += ",RESOLUTION=" + str(width) + "x" + str(height)
    if not _blank(transcode.codecs):
        result += ',CODECS="' + transcode.codecs + '"'
    if not _blank(transcode.frame_rate):
        result += ",FRAME-RATE=" + decimal_frame_rate(transcode.frame_rate)
    if not _blank(transcode.dynamic_range) and transcode.dynamic_range != "SDR":
        result += ",VIDEO-RANGE=" + ("HLG" if transcode.dynamic_range == "HLG" else "PQ")
    return result


def _language(value):
    if value is None or value.lower() == "und":
        return False
    parts = value.split("-")
    letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    if not 2 <= len(parts[0]) <= 3 or any(char not in letters for char in parts[0]):
        return False
    return all(2 <= len(part) <= 8 and all(char in letters + "0123456789" for char in part) for part in parts[1:])


@dataclass(frozen=True)
class MasterPlaylists:
    reads: HlsReads
    authorization: PlaybackAuthorization
    internal_base_url: str

    def get(self, context: PlaybackContext, user_id: str | None, timestamp=None, nonce=None, signature=None):
        video = self.reads.video(context.video_id)
        if video is None:
            return ""
        denied = self.authorization.authorize(video, context, user_id, timestamp, nonce, signature)
        if denied is not None:
            raise HlsDenied(denied, "该视频为私密视频" if denied == 35 else None)
        transcodes = [] if video.transcode_ids is None else self.reads.transcodes(video.transcode_ids)
        result = "#EXTM3U\n"
        for transcode in transcodes:
            if not successful(transcode) or not belongs_to_video(video, transcode) or not transcode.file_ids:
                continue
            url = self.internal_base_url + "/watchController/getM3u8Content.m3u8?"
            pairs = (("resolution", transcode.resolution), ("videoId", context.video_id),
                     ("clientId", context.client_id), ("sessionId", context.session_id), ("transcodeId", transcode.id))
            url += "&".join(key + "=" + _java(value) for key, value in pairs)
            url = self.authorization.sign_url(url, context)
            declaration, group = self._audio(transcode)
            result += declaration + "#EXT-X-STREAM-INF:BANDWIDTH=" + _java(transcode.max_bitrate)
            result += ",AVERAGE-BANDWIDTH=" + _java(transcode.average_bitrate) + media_attributes(transcode)
            if group is not None:
                result += ',AUDIO="' + group + '"'
            result += "\n" + url + "\n"
        return result

    def _audio(self, transcode):
        provider = transcode.current_provider if transcode.current_provider is not None else transcode.provider
        if provider != MPS or transcode.fallback_count != 1:
            return "", None
        audio = self.reads.audio(transcode.video_id)
        if audio is None or audio.track_count != 1 or audio.tracks is None or len(audio.tracks) != 1:
            return "", None
        language, channels = audio.tracks[0]
        if not _language(language) or channels is None or channels <= 0:
            return "", None
        group = "audio-" + transcode.id
        declaration = '#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="' + group + '",NAME="' + language
        declaration += '",LANGUAGE="' + language + '",DEFAULT=YES,AUTOSELECT=YES,CHANNELS="' + str(channels) + '"\n'
        return declaration, group
