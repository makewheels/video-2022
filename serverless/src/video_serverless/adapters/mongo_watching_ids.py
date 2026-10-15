"""Mongo allocates BSON ObjectIds; only their legacy hex strings leave the adapter."""


class MongoWatchingIds:
    def __init__(self, database_provider):
        self._database_provider = database_provider

    def _create(self, name, created_at, ip, user_agent):
        document = {"createTime": created_at, "ip": ip}
        if user_agent is not None:
            document["userAgent"] = user_agent
        result = self._database_provider().get_collection(name).insert_one(document)
        return str(result.inserted_id)

    def create_client(self, created_at, ip, user_agent):
        return self._create("client", created_at, ip, user_agent)

    def create_session(self, created_at, ip, user_agent):
        return self._create("session", created_at, ip, user_agent)
