"""Existing file constructor defaults and avatar reference update; no metadata refresh."""

from bson import ObjectId

from video_serverless.ports.avatars import AvatarFile


def _id(value):
    return ObjectId(value) if ObjectId.is_valid(value) else value


class MongoAvatars:
    def __init__(self, database_provider):
        self._database_provider = database_provider

    def create(self, file_id, uploader_id, key, created_at):
        document = {"_id": _id(file_id), "key": key, "fileType": "AVATAR", "createTime": created_at,
                    "updateTime": created_at, "deleted": False, "fileStatus": "CREATED",
                    "provider": "ALIYUN_OSS", "hasLink": False}
        if uploader_id is not None:
            document["uploaderId"] = uploader_id
        self._database_provider().get_collection("file").replace_one({"_id": _id(file_id)}, document, upsert=True)

    def find(self, file_id):
        document = self._database_provider().get_collection("file").find_one({"_id": _id(file_id)})
        if document is None:
            return None
        return AvatarFile(str(document["_id"]), document.get("uploaderId"),
                          document.get("key"), document.get("fileType"))

    def bind(self, user_id, file_id, updated_at):
        self._database_provider().get_collection("user").update_one(
            {"_id": _id(user_id)}, {"$set": {"avatarFileId": file_id, "updateTime": updated_at}}, upsert=False
        )
