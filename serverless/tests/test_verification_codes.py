from datetime import UTC, datetime, timedelta

import pytest
from pymongo.errors import DuplicateKeyError
from test_user_mongo_reads import isolated_database as isolated_database

from video_serverless.adapters.mongo_verification_codes import MongoVerificationCodes
from video_serverless.business.verification_codes import CodeRequests

NOW = datetime(2026, 1, 1, tzinfo=UTC)
PHONE = "00000000000"


@pytest.fixture
def codes(isolated_database):
    collection = isolated_database.verification_codes
    collection.delete_many({})
    collection.create_index("phone", unique=True)
    # TTL deletion is deliberately excluded: issuance must rely on record presence.
    return collection, MongoVerificationCodes(lambda: isolated_database)


def test_request_persists_legacy_fields_and_reuses_without_refresh(codes):
    collection, adapter = codes
    generated = []

    def generate():
        generated.append(True)
        return "0042"

    requests = CodeRequests(adapter, generate, lambda: NOW)
    assert requests.request(PHONE) is None
    first = collection.find_one({"phone": PHONE})
    assert set(first) == {"_id", "phone", "code", "createdAt"}
    assert first["code"] == "0042"
    assert first["createdAt"] == NOW.replace(tzinfo=None)
    assert requests.request(PHONE) is None
    assert collection.find_one({"phone": PHONE}) == first
    assert len(generated) == 1
    assert collection.count_documents({}) == 1


def test_old_record_is_not_replaced_before_mongo_ttl_removes_it(codes):
    collection, adapter = codes
    collection.insert_one({"phone": PHONE, "code": "fixture-old", "createdAt": NOW - timedelta(days=1)})
    original = collection.find_one()

    def unexpected():
        pytest.fail("Existing record must not generate a code or read the clock")

    CodeRequests(adapter, unexpected, unexpected).request(PHONE)
    assert collection.find_one() == original


def test_unique_conflict_propagates_without_overwriting_existing_record(codes):
    collection, adapter = codes
    adapter.save(PHONE, "0042", NOW)
    original = collection.find_one()
    with pytest.raises(DuplicateKeyError):
        adapter.save(PHONE, "9999", NOW + timedelta(seconds=1))
    assert collection.find_one() == original


def test_write_failure_is_not_reported_as_success():
    class Unavailable:
        def exists_for_phone(self, phone):
            return False

        def save(self, phone, code, created_at):
            raise RuntimeError("fixture write failure")

    with pytest.raises(RuntimeError, match="fixture write failure"):
        CodeRequests(Unavailable(), lambda: "0042", lambda: NOW).request(PHONE)
