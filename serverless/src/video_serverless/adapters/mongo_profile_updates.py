"""Legacy single profile update; no upsert, credential changes or application-level retries."""

from bson import ObjectId


class MongoProfileWrites:
    def __init__(self, database_provider):
        self._database_provider = database_provider

    def update_profile(self, user_id, nickname, bio, updated_at):
        key = ObjectId(user_id) if ObjectId.is_valid(user_id) else user_id
        # Reuse existing pool timeouts; the legacy void method ignores a zero match count.
        self._database_provider().get_collection("user").update_one(
            {"_id": key}, {"$set": {"nickname": nickname, "bio": bio, "updateTime": updated_at}}, upsert=False
        )
