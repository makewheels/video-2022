"""Test-only UTF-8 update HTTP composition; no production entrypoint or remote database."""

import json
from datetime import UTC, datetime

import pytest
from starlette.applications import Starlette
from starlette.concurrency import run_in_threadpool
from starlette.responses import Response
from starlette.routing import Route
from starlette.testclient import TestClient
from test_user_mongo_reads import golden_document
from test_user_mongo_reads import isolated_database as isolated_database
from test_user_reads import ROOT, comparison

from video_serverless.adapters.asgi_metadata import from_asgi_metadata
from video_serverless.adapters.mongo_profile_updates import MongoProfileWrites
from video_serverless.adapters.mongo_request_journal import MongoRequestJournal
from video_serverless.adapters.mongo_users import MongoUserReads
from video_serverless.business.profile_updates import ProfileUpdates
from video_serverless.business.request_identity import IdentityResolver
from video_serverless.profile_json import decode_profile
from video_serverless.request_cycle import RequestCycle
from video_serverless.user_responses import profile_update_response


@pytest.fixture
def composed(isolated_database):
    database = isolated_database
    database.user.delete_many({})
    database.requestLog.delete_many({})
    database.user.insert_one(golden_document())
    original = database.user.find_one()
    calls = []

    class ObservedWrites(MongoProfileWrites):
        def update_profile(self, *args):
            calls.append(1)
            return super().update_profile(*args)

    writes = ProfileUpdates(ObservedWrites(lambda: database), lambda: datetime(2025, 1, 1, tzinfo=UTC))
    lifecycle = RequestCycle(IdentityResolver(MongoUserReads(lambda: database)),
                             MongoRequestJournal(lambda: database), lambda: 1000)

    async def endpoint(request):
        metadata = from_asgi_metadata(request)
        body = await request.body()

        def handle(metadata, identity):
            value = decode_profile(body)
            return profile_update_response(writes.update(identity.user_id, value.nickname, value.bio))

        result = await run_in_threadpool(lifecycle.run, metadata, handle, protected=True)
        return Response(result.response.body, status_code=result.response.status, headers=result.response.headers)

    with TestClient(Starlette(routes=[Route("/user/updateProfile", endpoint, methods=["POST"])])) as client:
        yield database, original, client, calls


def golden(response, folder, name):
    expected = json.loads((ROOT / "contracts" / folder / (name + ".json")).read_text())
    actual = {"status": response.status_code, "contentType": response.headers["content-type"],
              "body": response.json()}
    assert comparison.compare(expected, actual)["match"]


def post(client, body, headers=None):
    return client.post("/user/updateProfile", content=body,
                       headers={"content-type": "application/json", "token": "fixture-legacy-token",
                                **(headers or {})})


@pytest.mark.parametrize("body,nickname,bio", [
    ('{"nickname":"updated","bio":" "}', "updated", " "),
    ('{}', None, None),
    ('{"nickname":1e2,"bio":true}', "1e2", "true"),
    ('{"nickname":"first","nickname":"last","ignored":[]}', "last", None),
])
def test_actual_update_http_matches_success_and_changes_only_legacy_fields(composed, body, nickname, bio):
    database, original, client, calls = composed
    golden(post(client, body.encode()), "profile-update", "success")
    assert database.user.find_one() == {**original, "nickname": nickname, "bio": bio,
                                       "updateTime": datetime(2025, 1, 1)}
    assert calls == [1]
    assert database.user.count_documents({}) == 1
    assert database.requestLog.count_documents({}) == 1
    log = database.requestLog.find_one()
    assert log["requestPath"] == "/user/updateProfile"
    assert log["request"]["method"] == "POST"
    assert "body" not in log["request"]
    assert log["response"] == {"httpStatus": 200}


@pytest.mark.parametrize("name,body", [
    ("nickname-long", {"nickname": "😀" * 16, "bio": "字" * 201}),
    ("bio-long", {"nickname": "valid", "bio": "字" * 201}),
])
def test_length_error_order_matches_java_without_business_write(composed, name, body):
    database, original, client, calls = composed
    golden(post(client, json.dumps(body).encode()), "profile-update", name)
    assert calls == []
    assert database.user.find_one() == original
    assert database.requestLog.count_documents({}) == 1


@pytest.mark.parametrize("body", [b'{', b'null', b'{"nickname":[]}',
                                  b'{"nickname":[],"nickname":"valid"}', b'{}{}', b'{} trailing'])
def test_binding_failure_matches_java_generic_error_and_is_logged(composed, body):
    database, original, client, calls = composed
    golden(post(client, body), "request-cycle", "binding-error")
    assert calls == []
    assert database.user.find_one() == original
    assert database.requestLog.count_documents({}) == 1
    assert database.requestLog.find_one()["response"] == {"httpStatus": 200}


@pytest.mark.parametrize("headers", [{}, {"token": "fixture-wrong"}])
def test_denied_request_does_not_bind_invalid_json_or_write_business_or_log(composed, headers):
    database, original, client, calls = composed
    response = client.post("/user/updateProfile", content=b'{',
                           headers={"content-type": "application/json", **headers})
    golden(response, "request-cycle", "unauthorized")
    assert calls == []
    assert database.user.find_one() == original
    assert database.requestLog.count_documents({}) == 0


def test_journal_mapping_failure_keeps_update_and_response_without_business_replay(composed):
    database, original, client, calls = composed
    response = post(client, b'{"nickname":"updated"}', {"X.Fixture": "fixture-value"})
    golden(response, "profile-update", "success")
    assert database.user.find_one() == {**original, "nickname": "updated", "bio": None,
                                       "updateTime": datetime(2025, 1, 1)}
    assert calls == [1]
    assert database.user.count_documents({}) == 1
    assert database.requestLog.count_documents({}) == 0
    denied = client.post("/user/updateProfile", content=b'{')
    golden(denied, "request-cycle", "unauthorized")
    assert database.requestLog.count_documents({}) == 0
