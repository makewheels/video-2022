"""Existing subscription records, full legacy channel mapping and narrow subscriber increments."""

from datetime import UTC

from bson.codec_options import CodecOptions

from video_serverless.adapters.mongo_users import MongoUserReads, _date, _text
from video_serverless.adapters.mongo_video_status import _id
from video_serverless.ports.subscription_writes import Subscription


class MongoSubscriptionWrites:
    def __init__(self, database_provider):
        self._database_provider = database_provider
        self._users = MongoUserReads(database_provider)

    def _collection(self):
        return self._database_provider().get_collection(
            "subscription", codec_options=CodecOptions(tz_aware=True, tzinfo=UTC))

    def channel_exists(self, channel_user_id):
        return self._users.find_by_id(channel_user_id) is not None

    def find_subscription(self, user_id, channel_user_id):
        row = self._collection().find_one({"userId": user_id, "channelUserId": channel_user_id})
        if row is None:
            return None
        return Subscription(_text(row["_id"]), _text(row.get("userId")), _text(row.get("channelUserId")),
                            _date(row.get("createTime")))

    def create_subscription(self, user_id, channel_user_id, created):
        self._collection().insert_one({"userId": user_id, "channelUserId": channel_user_id, "createTime": created})

    def remove_subscription(self, subscription_id):
        self._collection().delete_one({"_id": _id(subscription_id)})

    def increment_subscribers(self, channel_user_id, delta):
        self._database_provider().user.update_one({"_id": _id(channel_user_id)}, {"$inc": {"subscriberCount": delta}})
