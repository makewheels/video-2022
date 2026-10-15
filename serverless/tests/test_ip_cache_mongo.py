"""Real isolated Mongo IP cache; provider transport is synthetic and never calls paid APIs."""

from datetime import UTC, datetime
from decimal import Decimal

import pytest
from bson import ObjectId
from pymongo.errors import DuplicateKeyError
from test_user_mongo_reads import isolated_database as isolated_database

from video_serverless.adapters.aliyun_ip_api import AliyunIpApi
from video_serverless.adapters.cached_ip_locations import CachedIpLocations
from video_serverless.adapters.ip_json import IpMappingError, decode
from video_serverless.adapters.mongo_ip_cache import MongoIpCache
from video_serverless.adapters.mongo_watch_log import MongoWatchLogStore
from video_serverless.business.watch_log import WatchLogs
from video_serverless.ports.watch_log import WatchVisit

NOW = datetime(2024, 1, 2, 3, 4, 5, tzinfo=UTC)
IP = "2001:db8::1"
KEY = "2001_db8__1"
BODY = b'{"code":100,"ip":"outer","result":{"ip":"nested","nullable":null,"decimal":1.2500,"list":[null,1.50]}}'


@pytest.fixture
def database(isolated_database):
    isolated_database.ip_cache.drop()
    isolated_database.ip_cache.create_index("ip", unique=True)
    isolated_database.video.delete_many({})
    isolated_database.watchLog.delete_many({})
    return isolated_database


class Transport:
    def __init__(self, body=BODY, status=200):
        self.calls = []
        self.body = body
        self.status = status

    def __call__(self, url, headers):
        self.calls.append((url, headers))
        return self.status, self.body


def service(database, transport=None, cache=None):
    transport = Transport() if transport is None else transport
    return CachedIpLocations(MongoIpCache(lambda: database) if cache is None else cache,
                             AliyunIpApi("fixture-not-a-real-appcode", transport), lambda: NOW)


def test_miss_flattens_override_and_second_hit_drops_only_map_nulls(database):
    transport = Transport()
    locations = service(database, transport)
    result = locations.lookup(IP)
    assert result == {"code": 100, "ip": "nested", "nullable": None,
                      "decimal": Decimal("1.2500"), "list": [None, Decimal("1.50")]}
    saved = database.ip_cache.find_one()
    assert isinstance(saved["_id"], ObjectId)
    assert saved["ip"] == KEY and saved["createdAt"] == NOW.replace(tzinfo=None)
    assert "_class" not in saved
    assert "1.2500" in saved["locationJson"]
    assert "nullable" not in saved["locationJson"]
    second = locations.lookup(IP)
    assert second == {key: value for key, value in result.items() if key != "nullable"}
    assert transport.calls == [("https://ips.market.alicloudapi.com/iplocaltion?ip=" + IP,
                                {"Authorization": "APPCODE fixture-not-a-real-appcode"})]
    assert database.ip_cache.count_documents({}) == 1


def test_present_old_cache_is_used_without_refresh_or_provider(database):
    original = {"ip": KEY, "locationJson": '{"province":"fixture-cache"}', "createdAt": datetime(1970, 1, 1)}
    database.ip_cache.insert_one(original)
    before = database.ip_cache.find_one()
    transport = Transport()
    assert service(database, transport).lookup(IP) == {"province": "fixture-cache"}
    assert transport.calls == [] and database.ip_cache.find_one() == before


@pytest.mark.parametrize("text", ["", "null", None])
def test_present_empty_or_null_cache_does_not_fall_back_to_api(database, text):
    database.ip_cache.insert_one({"ip": KEY, "locationJson": text})
    transport = Transport()
    assert service(database, transport).lookup(IP) is None
    assert transport.calls == []


def test_existing_six_hour_ttl_and_unique_indexes_are_preserved_not_created_by_lookup(database):
    database.ip_cache.create_index("createdAt", expireAfterSeconds=21600)
    before = database.ip_cache.index_information()
    service(database).lookup(IP)
    after = database.ip_cache.index_information()
    assert after == before
    assert after["ip_1"]["unique"] is True
    assert after["createdAt_1"]["expireAfterSeconds"] == 21600


@pytest.mark.parametrize("body", [b'{"code":400}', b'null', b'{"result":null}', b'[]', b'not-json'])
def test_bad_provider_shape_fails_before_cache_save_without_retry(database, body):
    transport = Transport(body)
    with pytest.raises(IpMappingError):
        service(database, transport).lookup(IP)
    assert len(transport.calls) == 1 and database.ip_cache.count_documents({}) == 0


def test_non_success_http_status_does_not_replace_legacy_body_parsing(database):
    transport = Transport(status=400)
    assert service(database, transport).lookup(IP)["code"] == 100
    assert len(transport.calls) == 1


def test_network_failure_is_fixed_diagnostic_and_single_call_without_cache(database):
    class Unavailable:
        calls = 0

        def __call__(self, url, headers):
            self.calls += 1
            raise TimeoutError("fixture-provider-body-and-appcode-must-not-leak")

    transport = Unavailable()
    with pytest.raises(IpMappingError) as error:
        service(database, transport).lookup(IP)
    assert str(error.value) == "IP provider request failed"
    assert transport.calls == 1 and database.ip_cache.count_documents({}) == 0


def test_real_unique_race_preserves_other_cache_and_does_not_retry_provider(database):
    class RacingTransport(Transport):
        def __call__(self, url, headers):
            database.ip_cache.insert_one({"ip": KEY, "locationJson": '{"fixture":"winner"}'})
            return super().__call__(url, headers)

    transport = RacingTransport()
    with pytest.raises(DuplicateKeyError):
        service(database, transport).lookup(IP)
    assert len(transport.calls) == 1 and database.ip_cache.count_documents({}) == 1
    assert decode(database.ip_cache.find_one()["locationJson"]) == {"fixture": "winner"}


def test_actual_save_then_acknowledgement_failure_recovers_on_next_separate_lookup(database):
    class Interrupted(MongoIpCache):
        calls = 0

        def save(self, *args):
            self.calls += 1
            super().save(*args)
            raise RuntimeError("synthetic acknowledgement failure")

    cache = Interrupted(lambda: database)
    transport = Transport()
    locations = service(database, transport, cache)
    with pytest.raises(RuntimeError, match="synthetic acknowledgement failure"):
        locations.lookup(IP)
    assert len(transport.calls) == cache.calls == 1 and database.ip_cache.count_documents({}) == 1
    assert locations.lookup(IP)["ip"] == "nested"
    assert len(transport.calls) == cache.calls == 1


def test_watch_log_two_lookups_use_one_provider_call_and_real_cache(database):
    database.video.insert_one({"_id": "video", "status": "READY", "watch": {"watchCount": 0}})
    transport = Transport()
    logs = WatchLogs(MongoWatchLogStore(lambda: database), service(database, transport), lambda: NOW)
    assert logs.add(WatchVisit("video", "client", "session", None, IP, "fixture-agent"), "READY") == 0
    assert len(transport.calls) == 1
    assert database.watchLog.count_documents({}) == database.ip_cache.count_documents({}) == 1
    assert "nullable" in database.watchLog.find_one()["ipInfo"]
    assert "nullable" not in decode(database.ip_cache.find_one()["locationJson"])


def test_actual_loopback_transport_does_not_follow_redirect_or_forward_auth():
    from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
    from threading import Thread

    from video_serverless.adapters.aliyun_ip_api import _https

    received = []

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            received.append((self.path, self.headers.get("Authorization")))
            self.send_response(302)
            self.send_header("Location", f"http://localhost:{self.server.server_port}/next")
            self.end_headers()
            self.wfile.write(b"fixture-redirect")

        def log_message(self, *args):
            pass

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = Thread(target=server.serve_forever, daemon=True)
    try:
        thread.start()
        status, body = _https(f"http://127.0.0.1:{server.server_port}/start",
                              {"Authorization": "APPCODE fixture-not-real"})
        assert status == 302 and body == b"fixture-redirect"
        assert received == [("/start", "APPCODE fixture-not-real")]
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=5)
