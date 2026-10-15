"""Atomic reservations in the existing id_counters collection; no runtime indexes."""

from bson import Int64
from pymongo import ReturnDocument

from video_serverless.ports.identifiers import TokenSlot

# Keep the wire-layout epoch local to this concrete packed storage representation.
_EPOCH = 1288834974657


class MongoIdSequences:
    def __init__(self, database_provider, worker: int, datacenter: int):
        self._database_provider = database_provider
        self._token_key = f"tokenSnowflake:{datacenter}:{worker}"

    def next_long_sequence(self, time_unit):
        document = self._database_provider().id_counters.find_one_and_update(
            {"_id": "longId:" + str(time_unit)}, {"$inc": {"counter": Int64(1)}},
            upsert=True, return_document=ReturnDocument.AFTER,
        )
        return int(document["counter"])

    def reserve_token_slot(self, timestamp_ms):
        floor = Int64((timestamp_ms - _EPOCH) << 12)
        document = self._database_provider().id_counters.find_one_and_update(
            {"_id": self._token_key},
            [{"$set": {"counter": {"$max": [floor, {"$add": [{"$ifNull": ["$counter", Int64(-1)]},
                                                               Int64(1)]}]}}}],
            upsert=True, return_document=ReturnDocument.AFTER,
        )
        timestamp, sequence = divmod(int(document["counter"]), 4096)
        return TokenSlot(timestamp + _EPOCH, sequence)
