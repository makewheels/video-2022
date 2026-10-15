"""HLS compatibility and resource ownership checks, independent of HTTP transport."""

from dataclasses import replace

import pytest
from bson import ObjectId
from test_user_mongo_reads import isolated_database as isolated_database

from video_serverless.adapters.mongo_hls import MongoHlsReads
from video_serverless.business.hls import ChildPlaylists, HlsDenied, init_segment_uri
from video_serverless.business.playback_authorization import PlaybackAuthorization, PlaybackContext
from video_serverless.ports.hls import HlsFile, HlsTranscode, HlsVideo


class Signatures:
    def __init__(self):
        self.claims = []

    def sign(self, claim):
        self.claims.append(claim)
        return "signature"

    def verify(self, claim, signature):
        return signature == "grant"

    def timestamp_valid(self, timestamp):
        return timestamp == "1000"


class Reads:
    def __init__(self, content):
        self.saved_video = HlsVideo("video", "owner", "PUBLIC")
        self.saved_transcode = HlsTranscode("task", "video", "720p", ("init", "segment"), content)
        self.saved_files = [HlsFile("init", "video", "task", "init.mp4", None, "INIT"),
                            HlsFile("segment", "video", "task", "one.ts", 1, "TS")]
        self.file_reads = 0

    def video(self, video_id):
        return self.saved_video

    def transcode(self, transcode_id):
        return self.saved_transcode

    def files(self, file_ids):
        self.file_reads += 1
        return self.saved_files


def setup(content):
    reads = Reads(content)
    signatures = Signatures()
    authorization = PlaybackAuthorization(signatures, lambda: 1000, lambda: "nonce")
    return reads, signatures, ChildPlaylists(reads, authorization, "https://internal")


def test_ts_and_init_urls_preserve_original_comments_and_drop_trailing_empty_lines():
    reads, signatures, service = setup('#EXTM3U\r\n #EXT-X-MAP:URI="init.mp4" \r\n one.ts \r\nmissing.ts\n\n')
    result = service.get(PlaybackContext("video", None, "session"), "task", "720p", None)
    lines = result.split("\n")
    assert lines[0] == "#EXTM3U\r"
    assert lines[1].startswith('#EXT-X-MAP:URI="https://internal/file/access?resolution=720p&tsIndex=null')
    assert lines[1].endswith('&sign=signature"')
    assert "&clientId=null&sessionId=session&fileId=segment&timestamp=1000&nonce=nonce&sign=signature" in lines[2]
    assert lines[3] == "missing.ts"
    assert [claim.file_id for claim in signatures.claims] == ["init", "segment"]
    assert signatures.claims[0].client_id is None
    assert reads.file_reads == 1


@pytest.mark.parametrize("change", [
    "missing-video", "missing-task", "resolution", "foreign-video", "unreferenced-link",
])
def test_task_ownership_rejected_before_file_reads(change):
    reads, signatures, service = setup("one.ts")
    if change == "missing-video":
        reads.saved_video = None
    elif change == "missing-task":
        reads.saved_transcode = None
    elif change == "resolution":
        reads.saved_transcode = replace(reads.saved_transcode, resolution="1080p")
    else:
        reads.saved_video = replace(reads.saved_video, id="linked", has_link=change == "unreferenced-link",
                                    link_video_id="video", transcode_ids=())
    with pytest.raises(HlsDenied) as error:
        service.get(PlaybackContext("video", "c", "s"), "task", "720p", None)
    assert error.value.code == 35
    assert reads.file_reads == 0
    assert signatures.claims == []


def test_linked_video_uses_viewed_identity_in_signature_and_source_identity_for_ownership():
    reads, signatures, service = setup("one.ts")
    reads.saved_video = replace(reads.saved_video, id="linked", has_link=True,
                                link_video_id="video", transcode_ids=("task",))
    result = service.get(PlaybackContext("linked", "c", "s"), "task", "720p", None)
    assert "&videoId=linked&" in result
    assert signatures.claims[0].video_id == "linked"


@pytest.mark.parametrize("field,value", [("video_id", "other"), ("transcode_id", "other")])
def test_cross_resource_segment_denied(field, value):
    reads, signatures, service = setup("one.ts")
    reads.saved_files[1] = replace(reads.saved_files[1], **{field: value})
    with pytest.raises(HlsDenied, match="播放分片不属于当前视频"):
        service.get(PlaybackContext("video", "c", "s"), "task", "720p", None)
    assert signatures.claims == []


def test_private_requires_owner_or_playlist_grant():
    reads, _, service = setup("one.ts")
    reads.saved_video = replace(reads.saved_video, visibility="PRIVATE")
    context = PlaybackContext("video", "c", "s")
    with pytest.raises(HlsDenied):
        service.get(context, "task", "720p", None)
    assert reads.file_reads == 0
    assert service.get(context, "task", "720p", "owner").startswith("https://internal")
    assert service.get(context, "task", "720p", None, "1000", "nonce", "grant").startswith("https://internal")


@pytest.mark.parametrize("content,expected", [
    ('#EXT-X-MAP:URI="init.mp4"', "init.mp4"),
    ('#EXT-X-MAP:URI=init.mp4,BYTERANGE=10', "init.mp4"),
    ('#EXT-X-MAP:URI="init.mp4",BYTERANGE="10"', 'init.mp4",BYTERANGE="10'),
    ('#EXT-X-MAP:URI=""\n#EXT-X-MAP:URI="second"', None),
    ("#EXTM3U", None),
])
def test_legacy_map_parser_quirks(content, expected):
    assert init_segment_uri(content) == expected


def test_existing_mongo_documents_map_object_ids_links_and_segments(isolated_database):
    database = isolated_database
    video_id, task_id, file_id = ObjectId(), ObjectId(), ObjectId()
    database.video.insert_one({"_id": video_id, "visibility": "UNLISTED", "uploaderId": "owner",
                               "link": {"hasLink": True, "linkVideoId": "source"},
                               "transcodeIds": [str(task_id)]})
    database.transcode.insert_one({"_id": task_id, "videoId": "source", "resolution": "720p",
                                   "tsFileIds": [str(file_id)], "m3u8Content": "one.m4s\n"})
    database.tsFile.insert_one({"_id": file_id, "videoId": "source", "transcodeId": str(task_id),
                                "filename": "one.m4s", "tsIndex": 2, "fileType": "FMP4"})
    reads = MongoHlsReads(lambda: database)
    video = reads.video(str(video_id))
    task = reads.transcode(str(task_id))
    assert video.id == str(video_id) and video.transcode_ids == (str(task_id),)
    assert video.has_link and video.link_video_id == "source"
    assert task.content == "one.m4s\n"
    assert reads.files(task.file_ids) == [HlsFile(str(file_id), "source", str(task_id), "one.m4s", 2, "FMP4")]
    assert reads.video("missing") is None and reads.transcode("missing") is None
    assert reads.files(()) == []
