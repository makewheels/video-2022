"""Test-only ASGI composition; neither shipped entrypoint installs these routes."""

import json

import pytest
from starlette.applications import Starlette
from starlette.responses import Response
from starlette.routing import Route
from starlette.testclient import TestClient
from test_user_mongo_reads import golden_document
from test_user_mongo_reads import isolated_database as isolated_database
from test_user_reads import ROOT, comparison

from video_serverless.adapters.asgi_metadata import from_asgi_metadata
from video_serverless.adapters.mongo_avatar_files import MongoAvatarFiles
from video_serverless.adapters.mongo_request_journal import MongoRequestJournal
from video_serverless.adapters.mongo_users import MongoUserReads
from video_serverless.business.profiles import Profiles
from video_serverless.business.request_identity import IdentityResolver
from video_serverless.request_cycle import RequestCycle
from video_serverless.user_responses import channel_response, public_user_response


class FixtureSigner:
    def __init__(self):
        self.calls = []

    def sign(self, key, seconds):
        self.calls.append((key, seconds))
        return "https://example.invalid/signed-avatar.png"


@pytest.fixture
def composed(isolated_database):
    database = isolated_database
    for name in ("user", "file", "requestLog"):
        database[name].delete_many({})
    user = golden_document()
    user["avatarFileId"] = "fixture-avatar"
    database.user.insert_one(user)
    reads = MongoUserReads(lambda: database)
    signer = FixtureSigner()
    profiles = Profiles(reads, MongoAvatarFiles(lambda: database), signer)
    lifecycle = RequestCycle(IdentityResolver(reads), MongoRequestJournal(lambda: database), lambda: 1000)

    def endpoint(request):
        metadata = from_asgi_metadata(request)

        def handle(metadata, identity):
            if metadata.path == "/user/getMyProfile":
                return public_user_response(profiles.mine(identity.user_id))
            # Only test cases with the required userId are installed in this harness.
            user_id = metadata.first_parameter("userId")
            assert user_id is not None
            return channel_response(profiles.channel(user_id))

        result = lifecycle.run(metadata, handle, protected=metadata.path == "/user/getMyProfile")
        return Response(result.response.body, status_code=result.response.status, headers=result.response.headers)

    app = Starlette(routes=[Route("/user/getMyProfile", endpoint), Route("/user/getChannel", endpoint)])
    with TestClient(app) as client:
        yield database, signer, client


@pytest.mark.parametrize("case", ["profile-signed", "profile-fallback", "channel-signed",
                                  "channel-fallback", "channel-missing"])
def test_http_metadata_identity_reads_and_journal_match_java_goldens(composed, case):
    database, signer, client = composed
    if case.endswith("signed"):
        database.file.insert_one({"_id": "fixture-avatar", "key": "avatar/fixture-avatar"})
    original_user = database.user.find_one()
    original_file = database.file.find_one()
    if case.startswith("profile"):
        url = "/user/getMyProfile"
        headers = [("ToKeN", "fixture-legacy-token"), ("token", "fixture-wrong")]
    else:
        user_id = "fixture-missing" if case.endswith("missing") else "fixture-user-reads"
        url = "/user/getChannel?userId=" + user_id
        headers = [("token", "fixture-wrong")]
    response = client.get(url, headers=headers)
    expected = json.loads((ROOT / "contracts/profile-read" / (case + ".json")).read_text())
    actual = {"status": response.status_code, "contentType": response.headers["content-type"],
              "body": response.json()}
    assert comparison.compare(expected, actual)["match"]
    assert database.user.find_one() == original_user
    assert database.file.find_one() == original_file
    assert signer.calls == ([("avatar/fixture-avatar", 86400)] if case.endswith("signed") else [])
    assert database.requestLog.count_documents({}) == 1
    log = database.requestLog.find_one()
    path, _, query = url.partition("?")
    assert log["requestPath"] == path
    assert log["request"]["url"] == "http://testserver" + path
    assert log["request"].get("queryString") == (query or None)
    assert log["request"]["ip"] == "testclient"
    assert log["request"]["headerMap"]["token"] == headers[0][1]
    assert log["response"] == {"httpStatus": response.status_code}


@pytest.mark.parametrize("query,headers", [
    ("", []),
    ("?token=fixture-legacy-token", [("token", "fixture-wrong")]),
    ("?token=fixture-wrong&token=fixture-legacy-token", [("token", "")]),
])
def test_http_denial_matches_java_and_never_starts_journal(composed, query, headers):
    database, signer, client = composed
    response = client.get("/user/getMyProfile" + query, headers=headers)
    expected = json.loads((ROOT / "contracts/request-cycle/unauthorized.json").read_text())
    actual = {"status": response.status_code, "contentType": response.headers["content-type"],
              "body": response.json()}
    assert comparison.compare(expected, actual)["match"]
    assert database.requestLog.count_documents({}) == 0
    assert signer.calls == []


def test_http_journal_failure_keeps_response_and_next_request_has_no_stale_identity(composed):
    database, signer, client = composed
    expected = json.loads((ROOT / "contracts/profile-read/profile-fallback.json").read_text())
    response = client.get("/user/getMyProfile", headers={"token": "fixture-legacy-token", "X.Fixture": "value"})
    assert comparison.compare(expected, {"status": response.status_code,
                                         "contentType": response.headers["content-type"],
                                         "body": response.json()})["match"]
    assert database.requestLog.count_documents({}) == 0
    assert client.get("/user/getMyProfile").status_code == 401
    assert database.requestLog.count_documents({}) == 0
    assert client.get("/user/getMyProfile", headers={"token": "fixture-legacy-token"}).json()["code"] == 0
    assert database.requestLog.count_documents({}) == 1
    assert signer.calls == []
