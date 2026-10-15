from datetime import UTC, datetime, timedelta, timezone

import pytest
from starlette.testclient import TestClient
from test_user_mongo_reads import isolated_database as isolated_database

from video_serverless.adapters.mongo_cover_urls import MongoCoverUrls
from video_serverless.adapters.mongo_public_videos import MongoPublicVideos
from video_serverless.adapters.mongo_users import MongoUserReads
from video_serverless.adapters.user_asgi import create_user_asgi
from video_serverless.business.public_videos import PublicVideos
from video_serverless.public_video_application import PublicVideoApplication
from video_serverless.user_bootstrap import build_user_application

DATE = datetime(2024, 1, 2, 3, 4, 5, tzinfo=UTC)


class Signer:
    def __init__(self):
        self.calls = []

    def sign(self, key, seconds):
        self.calls.append((key, seconds))
        return "https://example.invalid/fixture-cover"


@pytest.fixture
def composed(isolated_database):
    database = isolated_database
    for name in ("user", "video", "cover", "requestLog", "exceptionLog"):
        database[name].delete_many({})
    signer = Signer()
    users = build_user_application(lambda: database, signer, profile="prod", local_clock=datetime.now,
                                   worker=1, datacenter=1)
    videos = PublicVideos(MongoPublicVideos(lambda: database, lambda: DATE), MongoCoverUrls(lambda: database, signer),
                          MongoUserReads(lambda: database),
                          lambda value: None if value is None else value.astimezone(
                              timezone(timedelta(hours=8))).strftime("%Y-%m-%d %H:%M:%S"))
    app = PublicVideoApplication(videos, users.cycle)
    with TestClient(create_user_asgi(app)) as client:
        yield database, signer, client


def insert(database, identity, title="fixture", visibility="PUBLIC", offset=0, **extra):
    database.video.insert_one({"_id": identity, "title": title, "visibility": visibility,
                               "createTime": DATE + timedelta(seconds=offset), "uploaderId": "fixture-uploader",
                               **extra})


def get(client, **params):
    response = client.get("/video/getPublicVideoList", params={"skip": "0", "limit": "20", **params})
    assert response.status_code == 200
    assert response.headers["content-type"] == "application/json"
    return response.json()


def test_public_nonready_page_zero_limit_negative_skip_and_total_match_java_fixture(composed):
    database, signer, client = composed
    insert(database, "fixture-old")
    insert(database, "fixture-new", offset=1)
    insert(database, "fixture-private", visibility="PRIVATE", offset=2)
    insert(database, "fixture-unlisted", visibility="UNLISTED", offset=3)
    page = get(client, skip="1", limit="1")["data"]
    assert page["total"] == 2 and page["list"][0]["id"] == "fixture-old"
    assert page["list"][0]["status"] == "CREATED"
    assert len(get(client, limit="0")["data"]["list"]) == 2
    assert get(client, skip="-1", limit="1")["data"]["list"][0]["id"] == "fixture-new"
    assert get(client, skip="2", limit="1")["data"] == {"list": [], "total": 2}
    assert signer.calls == []
    assert database.requestLog.count_documents({}) == 4


def test_literal_trim_case_tags_and_nonbreaking_space_are_not_new_regex_rules(composed):
    database, _, client = composed
    insert(database, "fixture-literal", title="literal .*", description="MixedCase", tags=["标签"])
    insert(database, "fixture-other", title="anything", offset=1)
    for keyword in ("  .*  ", "mixedcase", "标签"):
        data = get(client, keyword=keyword)["data"]
        assert data["total"] == 1 and data["list"][0]["id"] == "fixture-literal"
    assert get(client, keyword="   ")["data"]["total"] == 2
    assert get(client, keyword="\u00a0")["data"]["total"] == 0


def test_dates_nulls_names_old_links_and_cover_duration_match_old_source(composed):
    database, signer, client = composed
    database.user.insert_one({"_id": "fixture-uploader", "phone": "13800000000", "token": "fixture-old-token"})
    database.cover.insert_one({"_id": "fixture-cover", "key": "fixture/cover"})
    insert(database, "fixture-video", coverId="fixture-cover", mediaInfo=None,
           watch={"watchId": "fixture-watch", "watchUrl": "https://example.invalid/watch/fixture-watch",
                  "shortUrl": "https://example.invalid/s/fixture"})
    row = get(client)["data"]["list"][0]
    assert row["uploaderName"] == "138****0000" and row["watchCount"] == 0
    assert row["createTime"] == "2024-01-02T03:04:05.000Z"
    assert row["createTimeString"] == "2024-01-02 11:04:05"
    assert row["watchUrl"] == "https://example.invalid/watch/fixture-watch"
    assert row["shortUrl"] == "https://example.invalid/s/fixture"
    assert row["type"] is row["userId"] is row["duration"] is row["description"] is None
    assert row["coverUrl"] == "https://example.invalid/fixture-cover"
    assert signer.calls == [("fixture/cover", 7200)]


@pytest.mark.parametrize("params", [{"limit": "20"}, {"skip": "0"}, {"skip": "wrong", "limit": "20"},
                                   {"skip": "2147483648", "limit": "20"}])
def test_invalid_binding_uses_legacy_http_error_and_exception_journal(composed, params):
    database, _, client = composed
    response = client.get("/video/getPublicVideoList", params=params)
    assert response.status_code == 200
    assert response.json() == {"code": 1, "message": "未知错误", "data": None}
    assert database.exceptionLog.count_documents({}) == database.requestLog.count_documents({}) == 1


@pytest.mark.parametrize("value,expected", [("0x10", 16), ("#10", 16), ("010", 10), ("-0X10", -16),
                                           ("1 2", 12), ("\u200312\u2003", 12)])
def test_pagination_conversion_matches_actual_spring_numberutils_probe(value, expected):
    from video_serverless.ports.request_identity import RequestMetadata
    from video_serverless.public_video_application import _integer

    request = RequestMetadata("GET", "/fixture", "https://fixture.invalid/fixture", "127.0.0.1",
                              parameters=(("skip", value),))
    assert _integer(request, "skip") == expected
