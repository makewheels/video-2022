from urllib.parse import parse_qs, urlsplit

import pytest
from test_user_mongo_reads import isolated_database as isolated_database
from test_watch_info import composed as composed

MASTER = "/watchController/getMultivariantPlaylist.m3u8"
CHILD = "/watchController/getM3u8Content.m3u8"


def task(database):
    database.video.update_one({"_id": "fixture-video"}, {"$set": {"transcodeIds": ["fixture-task"]}})
    database.transcode.insert_one({"_id": "fixture-task", "videoId": "fixture-video", "resolution": "720p",
                                   "provider": "LOCAL_TRANSCODE", "status": "FINISHED",
                                   "tsFileIds": ["fixture-segment"], "m3u8Content": "#EXTM3U\r\none.ts\n\n"})
    database.tsFile.insert_one({"_id": "fixture-segment", "videoId": "fixture-video",
                                "transcodeId": "fixture-task", "filename": "one.ts", "tsIndex": 0, "fileType": "TS"})


@pytest.mark.parametrize("accept,content_type", [
    ("*/*", "text/plain;charset=UTF-8"), ("text/plain", "text/plain;charset=UTF-8"),
    ("application/vnd.apple.mpegurl", "application/vnd.apple.mpegurl;charset=UTF-8"),
    ("application/json", "application/json"),
])
def test_java_verified_mime_contract_and_single_journal(composed, accept, content_type):
    database, _, client = composed
    response = client.get(MASTER, params={"videoId": "fixture-video"}, headers={"Accept": accept})
    assert response.status_code == 200 and response.text == "#EXTM3U\n"
    assert response.headers["content-type"] == content_type
    assert database.requestLog.count_documents({}) == 1


def test_private_watch_info_to_master_to_child_preserves_signed_grants(composed):
    database, _, client = composed
    task(database)
    database.video.update_one({"_id": "fixture-video"}, {"$set": {"visibility": "PRIVATE"}})
    user = database.user.find_one({"_id": "fixture-user-reads"})
    info = client.get("/watchController/getWatchInfo", params={"watchId": "fixture-watch", "clientId": "c",
                                                             "sessionId": "s"}, headers={"token": user["token"]})
    master_url = info.json()["data"]["multivariantPlaylistUrl"]
    master = client.get(master_url)
    assert master.status_code == 200 and master.text.startswith("#EXTM3U\n#EXT-X-STREAM-INF")
    child_url = master.text.splitlines()[-1]
    child = client.get(child_url)
    assert child.text.startswith("#EXTM3U\r\nhttps://example.invalid/file/access?")
    segment_url = child.text.splitlines()[-1]
    query = parse_qs(urlsplit(segment_url).query)
    assert query["videoId"] == ["fixture-video"] and query["clientId"] == ["c"]
    assert query["sessionId"] == ["s"] and query["fileId"] == ["fixture-segment"]
    assert "playbackSign=" in child_url and "&sign=" in segment_url
    assert database.requestLog.count_documents({}) == 3
    assert database.exceptionLog.count_documents({}) == 0
    assert database.get_collection("client").count_documents({}) == 0


def test_private_denial_keeps_error_envelope_and_detail(composed):
    database, _, client = composed
    database.video.update_one({"_id": "fixture-video"}, {"$set": {"visibility": "PRIVATE"}})
    response = client.get(MASTER, params={"videoId": "fixture-video"})
    assert response.status_code == 200
    assert response.json() == {"code": 35, "message": "文件访问签名无效", "data": None}
    assert database.exceptionLog.find_one()["exceptionMessage"] == "该视频为私密视频"
    assert database.requestLog.count_documents({}) == 1


def test_binding_and_foreign_task_match_java_errors(composed):
    database, _, client = composed
    task(database)
    assert client.get(CHILD).json()["code"] == 1
    response = client.get(CHILD, params={"videoId": "other", "transcodeId": "fixture-task", "resolution": "720p"})
    assert response.json()["code"] == 35
    assert database.requestLog.count_documents({}) == 2


def test_missing_video_is_empty_and_child_uses_query_ids_not_headers(composed):
    database, _, client = composed
    assert client.get(MASTER, params={"videoId": "missing"}).text == ""
    task(database)
    response = client.get(CHILD, params={"videoId": "fixture-video", "transcodeId": "fixture-task",
                                         "resolution": "720p"},
                          headers={"clientId": "header-client", "sessionId": "header-session"})
    assert "&clientId=null&sessionId=null&" in response.text
    assert "header-client" not in response.text
