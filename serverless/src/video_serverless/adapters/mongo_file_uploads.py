"""Legacy File mapped-field replacement and Long object size, without new schema or retries."""

from datetime import UTC, datetime

from bson import Int64, ObjectId

_STRINGS = frozenset("_id uploaderId videoId rawFilename videoType fileStatus linkFileId linkFileKey "
                     "filename fileType key extension etag md5 acl provider storageClass".split())
_FIELDS = _STRINGS | frozenset("hasLink size uploadTime deleteTime createTime updateTime deleted".split())


def _id(value):
    return ObjectId(value) if ObjectId.is_valid(value) else value


class MongoUploadFiles:
    def __init__(self, database_provider, clock=None):
        self._database_provider = database_provider
        self._clock = clock or (lambda: datetime.now(UTC))

    def exists(self, file_id):
        return self._database_provider().file.find_one({"_id": _id(file_id)}, {"_id": 1}) is not None

    def owner(self, file_id):
        document = self._database_provider().file.find_one({"_id": _id(file_id)}, {"uploaderId": 1})
        value = None if document is None else document.get("uploaderId")
        return None if value is None else str(value)

    def find(self, file_id):
        return self._mapped(self._database_provider().file.find_one({"_id": _id(file_id)}))

    def find_by_key(self, key):
        return self._mapped(self._database_provider().file.find_one({"key": key}))

    def _mapped(self, document):
        if document is None:
            return None
        now = self._clock()
        defaults = {"createTime": now, "updateTime": now, "deleted": False, "hasLink": False,
                    "fileStatus": "CREATED", "provider": "ALIYUN_OSS"}
        mapped = {name: None if value is None else str(value) if name in _STRINGS
                  else int(value) if name == "size" else value
                  for name, value in document.items() if name in _FIELDS}
        return defaults | mapped

    def save(self, document):
        saved = {name: value for name, value in document.items() if name in _FIELDS and value is not None}
        saved["_id"] = _id(saved["_id"])
        if "size" in saved:
            saved["size"] = Int64(saved["size"])
        self._database_provider().file.replace_one({"_id": saved["_id"]}, saved, upsert=True)
