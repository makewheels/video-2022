"""Explicit writes in legacy order, preserving existing partial-failure behavior."""


class MongoCreationWrites:
    def __init__(self, database_provider):
        self._database_provider = database_provider

    def save_file(self, document):
        self._database_provider().file.replace_one({"_id": document["_id"]}, document, upsert=True)

    def save_video(self, document):
        self._database_provider().video.replace_one({"_id": document["_id"]}, document, upsert=True)
