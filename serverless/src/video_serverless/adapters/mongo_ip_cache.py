"""Existing ip_cache collection; no runtime index creation or application expiry check."""

from video_serverless.adapters.mongo_users import _text
from video_serverless.ports.ip_cache import IpCacheRecord


class MongoIpCache:
    def __init__(self, database_provider):
        self._database_provider = database_provider

    def find(self, key):
        document = self._database_provider().get_collection("ip_cache").find_one(
            {"ip": key}, {"locationJson": 1}, max_time_ms=1_000
        )
        return None if document is None else IpCacheRecord(_text(document.get("locationJson")))

    def save(self, key, location_json, created_at):
        self._database_provider().get_collection("ip_cache").insert_one(
            {"ip": key, "locationJson": location_json, "createdAt": created_at}
        )
