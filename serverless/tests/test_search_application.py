from datetime import UTC, datetime

import pytest
from starlette.testclient import TestClient
from test_user_mongo_reads import isolated_database as isolated_database

from video_serverless.adapters.user_asgi import create_user_asgi
from video_serverless.backend_bootstrap import build_backend_application
from video_serverless.business.video_search import VideoSearch


class NoMedia:
    def sign(self, key, seconds):
        pytest.fail("Search fixtures do not have covers")


@pytest.fixture
def composed(isolated_database):
    database = isolated_database
    for name in ("video", "user", "cover", "requestLog", "exceptionLog"):
        database[name].delete_many({})
    for index, category in enumerate(("音乐", "音乐", "科技")):
        database.video.insert_one({"_id": "fixture-video-" + str(index), "visibility": "PUBLIC",
                                   "title": "fixture .*", "category": category,
                                   "createTime": datetime(2024, 1, 1, 0, 0, index, tzinfo=UTC)})
    database.video.insert_one({"_id": "fixture-private", "visibility": "PRIVATE", "category": "音乐"})
    app = build_backend_application(lambda: database, NoMedia(), profile="prod", local_clock=datetime.now,
                                    display_timezone=UTC, worker=1, datacenter=1)
    with TestClient(create_user_asgi(app)) as client:
        yield database, client


def test_search_defaults_literal_query_and_category_equality_match_source(composed):
    database, client = composed
    data = client.get("/search").json()["data"]
    assert data["total"] == 3 and data["pageSize"] == 20 and data["currentPage"] == 0
    assert data["totalPages"] == 1 and len(data["content"]) == 3
    assert client.get("/search", params={"q": " .* ", "category": "音乐"}).json()["data"]["total"] == 2
    assert client.get("/search", params={"category": " 音乐 "}).json()["data"]["total"] == 0
    assert client.get("/search", params={"category": " "}).json()["data"]["total"] == 3
    assert database.requestLog.count_documents({}) == 4


@pytest.mark.parametrize("params,pages,length", [
    ({"pageSize": "0"}, 2147483647, 3),
    ({"pageSize": "-2"}, -1, 3),
    ({"page": "2147483647", "pageSize": "2"}, 2, 2),
    ({"page": "", "pageSize": ""}, 1, 3),
])
def test_existing_zero_negative_overflow_and_empty_defaults_are_not_new_validations(composed, params, pages, length):
    _, client = composed
    response = client.get("/search", params=params)
    assert response.status_code == 200
    assert response.json()["data"]["totalPages"] == pages
    assert len(response.json()["data"]["content"]) == length


def test_zero_size_empty_search_matches_java_nan_to_int(composed):
    database, client = composed
    database.video.delete_many({})
    assert client.get("/search", params={"pageSize": "0"}).json()["data"]["totalPages"] == 0


def test_category_order_is_the_existing_list_and_still_logs_request(composed):
    database, client = composed
    response = client.get("/category/list")
    assert response.headers["content-type"] == "application/json"
    assert response.json() == {"code": 0, "message": "success", "data": [
        "音乐", "游戏", "教育", "科技", "生活", "娱乐", "新闻", "体育",
        "动漫", "美食", "旅行", "知识", "影视", "搞笑", "其他",
    ]}
    assert database.requestLog.count_documents({}) == 1


def test_search_count_is_done_before_cover_or_user_enrichment():
    events = []

    class Reads:
        def page(self, *args):
            events.append("page")
            return ()

        def count(self, *args):
            events.append("count")
            return 0

    class Cards:
        def enrich(self, sources):
            events.append("enrich")
            return ()

    VideoSearch(Reads(), Cards()).search(None, None, 0, 20)
    assert events == ["page", "count", "enrich"]
