"""Legacy heartbeat save mapping; no retries or additional validation/ownership rules."""

from bson import ObjectId
from bson.decimal128 import Decimal128
from bson.int64 import Int64


class MongoHeartbeatStore:
    def __init__(self, database_provider):
        self._database_provider = database_provider

    def save(self, heartbeat):
        fields = {"videoId": heartbeat.video_id, "clientId": heartbeat.client_id,
                  "sessionId": heartbeat.session_id, "viewerId": heartbeat.viewer_id,
                  "videoStatus": heartbeat.video_status, "playerProvider": heartbeat.player_provider,
                  "clientTime": heartbeat.client_time, "createTime": heartbeat.created_at,
                  "type": heartbeat.type, "event": heartbeat.event, "playerStatus": heartbeat.player_status}
        document = {key: value for key, value in fields.items() if value is not None}
        if heartbeat.player_time is not None:
            document["playerTime"] = Int64(heartbeat.player_time)
        if heartbeat.player_volume is not None:
            document["playerVolume"] = Decimal128(heartbeat.player_volume)
        collection = self._database_provider().get_collection("heartbeat")
        if heartbeat.id is None:
            collection.insert_one(document)
        else:
            key = ObjectId(heartbeat.id) if ObjectId.is_valid(heartbeat.id) else heartbeat.id
            document["_id"] = key
            collection.replace_one({"_id": key}, document, upsert=True)
