from dataclasses import replace

import pytest
from test_hls_child import Reads, Signatures
from test_user_mongo_reads import isolated_database as isolated_database

from video_serverless.adapters.mongo_hls import MongoHlsReads
from video_serverless.business.hls import HlsDenied
from video_serverless.business.hls_master import MasterPlaylists, decimal_frame_rate, successful
from video_serverless.business.playback_authorization import PlaybackAuthorization, PlaybackContext
from video_serverless.ports.hls import HlsAudio


class MasterReads(Reads):
    def __init__(self):
        super().__init__("one.ts")
        self.saved_video = replace(self.saved_video, transcode_ids=("task",))
        self.saved_transcode = replace(self.saved_transcode, provider="LOCAL_TRANSCODE", status="FINISHED")
        self.saved_audio = HlsAudio(1, (("en-US", 2),))
        self.audio_reads = []

    def transcodes(self, ids):
        assert ids == ("task",)
        return [self.saved_transcode]

    def audio(self, video_id):
        self.audio_reads.append(video_id)
        return self.saved_audio


def setup():
    reads = MasterReads()
    signatures = Signatures()
    service = MasterPlaylists(reads, PlaybackAuthorization(signatures, lambda: 1000, lambda: "nonce"), "https://base")
    return reads, signatures, service


def test_exact_variant_attributes_actual_dimensions_and_signed_url():
    reads, signatures, service = setup()
    reads.saved_transcode = replace(reads.saved_transcode, max_bitrate=1000, average_bitrate=800,
                                    width=1280, height=720, actual_width=640, actual_height=360,
                                    codecs="avc1.fixture", frame_rate="60000/1001", dynamic_range="HLG")
    result = service.get(PlaybackContext("video", None, "session"), None)
    assert result == ('#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000,AVERAGE-BANDWIDTH=800'
                      ',RESOLUTION=640x360,CODECS="avc1.fixture",FRAME-RATE=59.940,VIDEO-RANGE=HLG\n'
                      'https://base/watchController/getM3u8Content.m3u8?resolution=720p&videoId=video'
                      '&clientId=null&sessionId=session&transcodeId=task&playbackTime=1000'
                      '&playbackNonce=nonce&playbackSign=signature\n')
    assert signatures.claims[0].file_id == "playlist"
    assert reads.audio_reads == []


@pytest.mark.parametrize("provider,status,expected", [
    (None, "FINISHED", False), ("UNKNOWN", "FINISHED", False),
    ("ALIYUN_MPS_TRANSCODE", "TranscodeSuccess", True), ("ALIYUN_MPS_TRANSCODE", "FINISHED", False),
    ("LOCAL_TRANSCODE", "FINISHED", True), ("LOCAL_TRANSCODE", "FAILED", False),
    ("ALIYUN_CLOUD_FUNCTION_TRANSCODE", "FINISHED", True),
    ("ALIYUN_CLOUD_FUNCTION_TRANSCODE_CPU", "FINISHED", True),
    ("ALIYUN_CLOUD_FUNCTION_TRANSCODE_GPU", "FINISHED", True),
])
def test_success_status_matches_provider(provider, status, expected):
    reads = MasterReads()
    assert successful(replace(reads.saved_transcode, provider=provider, status=status)) == expected


@pytest.mark.parametrize("changes", [
    {"status": "FAILED"}, {"file_ids": ()}, {"file_ids": None}, {"video_id": "foreign"},
    {"current_provider": "UNKNOWN"},
])
def test_ineligible_variants_are_omitted(changes):
    reads, signatures, service = setup()
    reads.saved_transcode = replace(reads.saved_transcode, **changes)
    assert service.get(PlaybackContext("video", "c", "s"), None) == "#EXTM3U\n"
    assert signatures.claims == []


def test_missing_video_empty_and_private_auth_before_transcodes():
    reads, _, service = setup()
    reads.saved_video = None
    assert service.get(PlaybackContext("missing", "c", "s"), None) == ""
    reads.saved_video = replace(MasterReads().saved_video, visibility="PRIVATE")
    with pytest.raises(HlsDenied, match="该视频为私密视频"):
        service.get(PlaybackContext("video", "c", "s"), None)
    assert service.get(PlaybackContext("video", "c", "s"), "owner").startswith("#EXTM3U\n#EXT-X-STREAM-INF")


def test_mps_fallback_muxed_audio_and_linked_source():
    reads, _, service = setup()
    reads.saved_video = replace(reads.saved_video, id="linked", has_link=True, link_video_id="video")
    reads.saved_transcode = replace(reads.saved_transcode, current_provider="ALIYUN_MPS_TRANSCODE",
                                    status="TranscodeSuccess", fallback_count=1)
    result = service.get(PlaybackContext("linked", "c", "s"), None)
    assert ('#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio-task",NAME="en-US",LANGUAGE="en-US",'
            'DEFAULT=YES,AUTOSELECT=YES,CHANNELS="2"\n') in result
    assert ',AUDIO="audio-task"\n' in result
    assert reads.audio_reads == ["video"]
    assert "&videoId=linked&" in result


@pytest.mark.parametrize("audio", [
    None, HlsAudio(2, (("en", 2),)), HlsAudio(1, None), HlsAudio(1, ()),
    HlsAudio(1, (("und", 2),)), HlsAudio(1, (("UND", 2),)), HlsAudio(1, (("en_US", 2),)),
    HlsAudio(1, (("en", 0),)), HlsAudio(1, (("en", None),)), HlsAudio(1, (("en", 2), ("zh", 2))),
])
def test_unproven_audio_metadata_omits_audio_declaration(audio):
    reads, _, service = setup()
    reads.saved_transcode = replace(reads.saved_transcode, current_provider="ALIYUN_MPS_TRANSCODE",
                                    status="TranscodeSuccess", fallback_count=1)
    reads.saved_audio = audio
    result = service.get(PlaybackContext("video", "c", "s"), None)
    assert "#EXT-X-MEDIA" not in result and ",AUDIO=" not in result
    assert "BANDWIDTH=null,AVERAGE-BANDWIDTH=null\n" in result


@pytest.mark.parametrize("value,expected", [
    ("60000/1001", "59.940"), ("24", "24.000"), ("1/16", "0.063"),
    ("-1/16", "-0.063"), ("-0.0001", "0.000"), ("30/", "30.000"),
])
def test_java_decimal_rounding(value, expected):
    assert decimal_frame_rate(value) == expected


def test_mongo_master_fields_and_muxed_audio(isolated_database):
    database = isolated_database
    database.transcode.insert_one({"_id": "master-fixture", "videoId": "source-master-fixture",
                                   "provider": "LOCAL_TRANSCODE", "currentProvider": "ALIYUN_MPS_TRANSCODE",
                                   "status": "TranscodeSuccess", "fallbackCount": 1, "tsFileIds": ["file"],
                                   "actualWidth": 640, "actualFrameRate": "30000/1001", "maxBitrate": 1000})
    database.video.insert_one({"_id": "source-master-fixture", "mediaInfo": {
        "audioTrackCount": 1, "audioTracks": {"tracks": [{"language": "zh", "channels": 2}]}}})
    reads = MongoHlsReads(lambda: database)
    task = reads.transcodes(("master-fixture",))[0]
    assert task.actual_width == 640 and task.frame_rate == "30000/1001" and task.max_bitrate == 1000
    assert successful(task)
    assert reads.audio("source-master-fixture") == HlsAudio(1, (("zh", 2),))
    assert reads.audio("missing") is None
