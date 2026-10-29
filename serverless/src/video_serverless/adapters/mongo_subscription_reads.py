"""Exact reference matches and legacy date-ordered subscription lists, without writes."""

from video_serverless.adapters.mongo_users import _text


class MongoSubscriptionReads:
    def __init__(self, database_provider):
        self._database_provider = database_provider

    def subscribed(self, user_id, channel_user_id):
        return self._database_provider().subscription.find_one(
            {"userId": user_id, "channelUserId": channel_user_id}) is not None

    def channels(self, user_id, skip, limit):
        # Spring Query treats non-positive limits as unlimited, not PyMongo's hard negative limit.
        cursor = self._database_provider().subscription.find({"userId": user_id}).sort(
            "createTime", -1).skip(max(skip, 0)).limit(max(limit, 0))
        with cursor:
            return tuple(_text(row.get("channelUserId")) for row in cursor)
