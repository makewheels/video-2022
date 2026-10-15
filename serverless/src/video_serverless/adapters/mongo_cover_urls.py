"""Legacy batch cover lookup, two-hour signing, with shared object URL capability."""

from bson import ObjectId


class MongoCoverUrls:
    def __init__(self, database_provider, urls):
        self._database_provider = database_provider
        self._urls = urls

    def signed(self, cover_ids):
        keys = [ObjectId(value) if ObjectId.is_valid(value) else value for value in cover_ids]
        documents = list(self._database_provider().get_collection("cover").find({"_id": {"$in": keys}}))
        by_key = {}
        for document in documents:
            key = document.get("key")
            by_key[key] = self._urls.sign(key, 7200)
        return {str(document["_id"]): by_key[document.get("key")] for document in documents}
