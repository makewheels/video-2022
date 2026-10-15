"""Legacy verification_codes mapping; indexes remain an explicit deployment concern."""


class MongoVerificationCodes:
    def __init__(self, database_provider):
        self._database_provider = database_provider

    def exists_for_phone(self, phone):
        return self._database_provider().get_collection("verification_codes").find_one(
            {"phone": phone}, {"_id": 1}
        ) is not None

    def save(self, phone, code, created_at):
        # Preserve the legacy find-then-insert semantics and propagate duplicate/write failures.
        # No retry, upsert, TTL refresh or runtime index creation.
        self._database_provider().get_collection("verification_codes").insert_one(
            {"phone": phone, "code": code, "createdAt": created_at}
        )
