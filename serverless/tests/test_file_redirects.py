from dataclasses import replace

import pytest
from bson import ObjectId
from test_user_mongo_reads import isolated_database as isolated_database

from video_serverless.adapters.hmac_access_signatures import HmacAccessSignatures
from video_serverless.adapters.mongo_file_redirects import MongoFileRedirectKeys
from video_serverless.business.file_access import FileRedirects
from video_serverless.ports.playback_signatures import AccessClaim

NOW = 1700000000000
CLAIM = AccessClaim("fixture-video", "c", "s", "720p", "fixture-file", str(NOW), "fixture-nonce")


class Storage:
    def __init__(self, key="fixture/segment.ts"):
        self.key = key
        self.calls = []

    def key_for_file(self, file_id):
        self.calls.append(("key", file_id))
        return self.key

    def sign(self, key, seconds):
        self.calls.append(("sign", key, seconds))
        return "https://example.invalid/fixture?Signature=synthetic"


def test_valid_claim_reads_once_and_signs_for_exactly_three_hours():
    storage = Storage()
    signatures = HmacAccessSignatures("fixture-secret", lambda: NOW)
    result = FileRedirects(storage, storage, signatures).get(CLAIM, signatures.sign(CLAIM))
    assert result.url == "https://example.invalid/fixture?Signature=synthetic" and result.error_code is None
    assert storage.calls == [("key", CLAIM.file_id), ("sign", storage.key, 10800)]
    assert "Signature" not in repr(result)


@pytest.mark.parametrize("timestamp", [None, "", "0", str(NOW - 300001), "invalid"])
def test_time_rejection_precedes_hmac_and_storage(timestamp):
    storage = Storage()
    # An empty secret would fail HMAC calculation if time validation did not short-circuit.
    signatures = HmacAccessSignatures("", lambda: NOW)
    result = FileRedirects(storage, storage, signatures).get(replace(CLAIM, timestamp=timestamp), "wrong")
    assert result.error_code == 35 and result.detail == "文件访问签名已过期"
    assert storage.calls == []


@pytest.mark.parametrize("field", ["video_id", "client_id", "session_id", "resolution", "file_id", "nonce"])
def test_signed_context_substitution_cannot_reach_storage(field):
    storage = Storage()
    signatures = HmacAccessSignatures("fixture-secret", lambda: NOW)
    result = FileRedirects(storage, storage, signatures).get(replace(CLAIM, **{field: "other"}), signatures.sign(CLAIM))
    assert result.error_code == 35 and result.detail is None and result.url is None
    assert storage.calls == []


def test_missing_key_does_not_call_signer_but_empty_key_preserves_old_semantics():
    signatures = HmacAccessSignatures("fixture-secret", lambda: NOW)
    storage = Storage(None)
    result = FileRedirects(storage, storage, signatures).get(CLAIM, signatures.sign(CLAIM))
    assert result.error_code == 31 and storage.calls == [("key", CLAIM.file_id)]
    storage = Storage("")
    assert FileRedirects(storage, storage, signatures).get(CLAIM, signatures.sign(CLAIM)).error_code is None
    assert storage.calls[-1] == ("sign", "", 10800)


@pytest.mark.parametrize("key,expected", [
    (None, None), ("", ""), (" ", " "), (42, "42"), ("fixture/init.mp4", "fixture/init.mp4"),
])
def test_actual_mongo_key_mapping_has_no_owner_type_or_deleted_filters(isolated_database, key, expected):
    database = isolated_database
    document = {"_id": "fixture-file", "key": key, "uploaderId": "someone-else", "deleted": True, "fileType": "INIT"}
    database.tsFile.insert_one(document.copy())
    adapter = MongoFileRedirectKeys(lambda: database)
    assert adapter.key_for_file("fixture-file") == expected
    assert database.tsFile.find_one({"_id": "fixture-file"}) == document
    database.tsFile.delete_one({"_id": "fixture-file"})


def test_valid_objectid_never_falls_back_to_same_text_string(isolated_database):
    database = isolated_database
    identity = ObjectId()
    database.tsFile.insert_one({"_id": str(identity), "key": "fixture/string"})
    adapter = MongoFileRedirectKeys(lambda: database)
    assert adapter.key_for_file(str(identity)) is None
    database.tsFile.insert_one({"_id": identity, "key": "fixture/object-id"})
    assert adapter.key_for_file(str(identity)) == "fixture/object-id"
    assert adapter.key_for_file("fixture-missing") is None
    assert adapter.key_for_file(None) is None


def test_actual_mongo_absent_key_and_absent_document_are_both_missing(isolated_database):
    database = isolated_database
    database.tsFile.insert_one({"_id": "fixture-no-key"})
    assert MongoFileRedirectKeys(lambda: database).key_for_file("fixture-no-key") is None
    database.tsFile.delete_one({"_id": "fixture-no-key"})


def test_signer_failure_propagates_once_without_retry_or_writes():
    class FailingStorage(Storage):
        def sign(self, key, seconds):
            super().sign(key, seconds)
            raise RuntimeError("Synthetic signer unavailable")
    storage = FailingStorage()
    signatures = HmacAccessSignatures("fixture-secret", lambda: NOW)
    with pytest.raises(RuntimeError, match="Synthetic signer unavailable"):
        FileRedirects(storage, storage, signatures).get(CLAIM, signatures.sign(CLAIM))
    assert storage.calls == [("key", CLAIM.file_id), ("sign", storage.key, 10800)]
