from datetime import datetime

import pytest
from bson import ObjectId
from bson.int64 import Int64
from test_request_cycle import cycle, metadata, ok
from test_user_mongo_reads import isolated_database as isolated_database

from video_serverless.adapters.mongo_request_journal import JournalMappingError, MongoRequestJournal
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.ports.request_journal import RequestJournalEntry


@pytest.fixture
def database(isolated_database):
    isolated_database.requestLog.delete_many({})
    return isolated_database


def test_actual_bson_matches_legacy_synthetic_metadata_and_numeric_types(database):
    request = RequestMetadata("GET", "/user/getChannel", "http://localhost/user/getChannel", "192.0.2.10",
                              (("token", "fixture-context-token"), ("TOKEN", "fixture-second-token"),
                               ("User-Agent", "fixture-first-agent"), ("User-Agent", "fixture-second-agent")),
                              raw_query="userId=fixture-missing&token=fixture-query-token")
    journal = MongoRequestJournal(lambda: database)
    journal.save(RequestJournalEntry(request, 1000, 1025, 200))
    raw = database.requestLog.find_one()
    assert isinstance(raw.pop("_id"), ObjectId)
    assert raw == {"requestPath": request.path,
                   "request": {"url": request.url, "path": request.path, "method": "GET",
                               "headerMap": {"token": "fixture-context-token", "User-Agent": "fixture-first-agent"},
                               "ip": "192.0.2.10", "userAgent": "fixture-first-agent",
                               "queryString": request.raw_query},
                   "response": {"httpStatus": 200}, "startTime": datetime(1970, 1, 1, 0, 0, 1),
                   "endTime": datetime(1970, 1, 1, 0, 0, 1, 25000), "timeCost": Int64(25)}
    assert isinstance(raw["timeCost"], Int64)
    assert type(raw["response"]["httpStatus"]) is int


@pytest.mark.parametrize("query", [None, ""])
def test_missing_fields_omitted_empty_query_preserved_and_clock_not_clamped(database, query):
    request = RequestMetadata("POST", "/fixture", "http://localhost/fixture", "192.0.2.10", raw_query=query)
    MongoRequestJournal(lambda: database).save(RequestJournalEntry(request, 1025, 1000, 200))
    raw = database.requestLog.find_one()
    assert raw["timeCost"] == -25
    assert ("queryString" in raw["request"]) == (query is not None)
    assert raw["request"].get("queryString") == query
    assert "userAgent" not in raw["request"]
    assert "body" not in raw["request"]
    assert raw["response"] == {"httpStatus": 200}
    assert "_class" not in raw


def test_actual_cycle_inserts_once_and_denied_or_excluded_requests_do_not_insert(database):
    journal = MongoRequestJournal(lambda: database)
    assert cycle(journal=journal).run(metadata(), ok, protected=True).journal_status == "saved"
    assert database.requestLog.count_documents({}) == 1
    denied = cycle(journal=journal).run(metadata(token="fixture-wrong"), ok, protected=True)
    assert denied.journal_status == "not_started"
    assert cycle(journal=journal).run(metadata("/file/access"), ok).journal_status == "skipped"
    assert database.requestLog.count_documents({}) == 1


def test_failed_storage_keeps_business_write_and_response_without_replaying(database):
    attempts = []

    def unavailable():
        attempts.append(1)
        raise RuntimeError("fixture-private-storage-error")

    def handler(request, identity):
        database.user.insert_one({"_id": "fixture-journal-business-write"})
        return ok(request, identity)

    database.user.delete_many({})
    result = cycle(journal=MongoRequestJournal(unavailable)).run(metadata(), handler, protected=True)
    assert result.response.status == 200
    assert result.journal_status == "failed"
    assert attempts == [1]
    assert database.user.count_documents({}) == 1
    assert database.requestLog.count_documents({}) == 0


def test_unverified_dotted_header_mapping_fails_without_contacting_database():
    def forbidden():
        pytest.fail("Unsupported mapping must not contact a database")

    request = RequestMetadata("GET", "/fixture", "http://localhost/fixture", "192.0.2.10", (("X.Fixture", "value"),))
    with pytest.raises(JournalMappingError, match="Dotted header storage is not verified"):
        MongoRequestJournal(forbidden).save(RequestJournalEntry(request, 1000, 1025, 200))
