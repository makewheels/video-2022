"""Existing user, verification_codes and Decimal128 wallet mappings."""

from bson import Decimal128, Int64, ObjectId

from video_serverless.adapters.mongo_users import MongoUserReads


class MongoLoginStore:
    def __init__(self, database_provider):
        self._database_provider = database_provider

    def code_for_phone(self, phone):
        document = self._database_provider().verification_codes.find_one({"phone": phone})
        if document is None:
            return None
        if not isinstance(document.get("code"), str):
            raise ValueError("Verification code BSON shape is not supported")
        return document["code"]

    def remove_codes(self, phone):
        self._database_provider().verification_codes.delete_many({"phone": phone})

    def user_for_phone(self, phone):
        return MongoUserReads(self._database_provider)._read({"phone": phone})

    def save_user(self, user):
        key = ObjectId(user.id) if ObjectId.is_valid(user.id) else user.id
        # MongoConfiguration explicitly disables Spring's _class discriminator.
        document = {"_id": key}
        for source, target in (
            ("phone", "phone"), ("register_channel", "registerChannel"),
            ("create_time", "createTime"), ("update_time", "updateTime"), ("token", "token"),
            ("nickname", "nickname"), ("avatar_file_id", "avatarFileId"), ("avatar_url", "avatarUrl"),
            ("banner_url", "bannerUrl"), ("bio", "bio"),
            ("subscriber_count", "subscriberCount"), ("video_count", "videoCount"),
        ):
            value = getattr(user, source)
            if value is not None:
                if source in {"subscriber_count", "video_count"}:
                    value = Int64(value)
                document[target] = value
        self._database_provider().user.replace_one({"_id": key}, document, upsert=True)

    def ensure_wallet(self, user_id, created_at):
        collection = self._database_provider().wallet
        if collection.find_one({"userId": user_id}, {"_id": 1}) is not None:
            return
        collection.insert_one({"userId": user_id, "createTime": created_at, "updateTime": created_at,
                               "balance": Decimal128("0")})
