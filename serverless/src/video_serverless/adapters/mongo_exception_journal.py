"""Synchronous writes into the existing exceptionLog collection, without retries."""


class MongoExceptionJournal:
    def __init__(self, database_provider):
        self._database_provider = database_provider

    def save(self, entry):
        document = {"createTime": entry.created_at, "exceptionStackTrace": entry.stack_trace}
        if entry.exception_message is not None:
            document["exceptionMessage"] = entry.exception_message
        if entry.system_error_code is not None:
            document["systemErrorCode"] = entry.system_error_code
            document["systemErrorMessage"] = entry.system_error_message
        self._database_provider().get_collection("exceptionLog").insert_one(document)
