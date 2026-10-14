"""Legacy avatar file key reads only; no object access, ownership filter or writes."""

from bson import ObjectId


class AvatarFileMappingError(ValueError):
    """An unverified file BSON shape; record values are never included."""


class MongoAvatarFiles:
    def __init__(self, database_provider):
        self._database_provider = database_provider

    def key_for_file(self, file_id: str) -> str | None:
        key = ObjectId(file_id) if ObjectId.is_valid(file_id) else file_id
        # Reuse the existing lazy pool; the legacy read does not filter type/owner/status.
        document = self._database_provider().get_collection("file").find_one(
            {"_id": key}, max_time_ms=1_000
        )
        if document is None:
            return None
        value = document.get("key")
        if value is None or isinstance(value, str):
            return value
        if isinstance(value, int) and not isinstance(value, bool):
            return str(value)
        raise AvatarFileMappingError("Avatar file key conversion is not verified for this BSON type")
