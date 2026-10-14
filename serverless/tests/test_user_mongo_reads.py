"""Actual isolated Mongo + Java HTTP fixtures; never use a remote or shared database."""

import json
import os
from datetime import UTC, datetime
from uuid import uuid4

import pytest
from bson import ObjectId
from pymongo import MongoClient
from pymongo.uri_parser import parse_uri
from test_user_reads import ROOT, comparison

from video_serverless.adapters.mongo_users import MongoUserReads, UserMappingError
from video_serverless.business.profiles import Profiles
from video_serverless.business.users import Users
from video_serverless.user_responses import channel_response, public_user_response, token_user_response


@pytest.fixture(scope="module")
def isolated_database():
    uri = os.environ.get("MONGODB_URI")
    if not uri:
        pytest.skip("Actual local MongoDB connection has not been configured")
    parsed = parse_uri(uri)
    if (any(host not in {"localhost", "127.0.0.1", "::1"} for host, _ in parsed["nodelist"])
            or parsed["username"] or parsed["password"] or parsed["options"].get("replicaset")):
        pytest.fail("User fixture writes require unauthenticated loopback standalone MongoDB")
    name = "video-user-read-test-" + uuid4().hex
    client = MongoClient(uri, serverSelectionTimeoutMS=3_000, connectTimeoutMS=3_000,
                         socketTimeoutMS=3_000, maxPoolSize=2)
    try:
        hello = client.admin.command("hello")
        assert not hello.get("setName") and hello.get("msg") != "isdbgrid", "Standalone MongoDB required"
        yield client[name]
    finally:
        try:
            client.drop_database(name)
        finally:
            client.close()


@pytest.fixture
def database(isolated_database):
    isolated_database.user.delete_many({})
    return isolated_database


def golden_document():
    return {
        "_id": "fixture-user-reads", "phone": "00000000000", "registerChannel": "TEST",
        "createTime": datetime(2024, 1, 2, 3, 4, 5, tzinfo=UTC),
        "updateTime": datetime(2024, 1, 2, 3, 4, 5, 678000, tzinfo=UTC),
        "token": "fixture-legacy-token", "nickname": "测试读取",
        "avatarFileId": None, "avatarUrl": "https://example.invalid/avatar.png",
        "bannerUrl": None, "bio": None, "subscriberCount": 12, "videoCount": 7,
    }


@pytest.mark.parametrize("case", ["token-found", "token-missing", "id-found", "id-missing"])
def test_mongo_use_case_response_matches_actual_java_http_golden(database, case):
    original = golden_document()
    database.user.insert_one(original.copy())
    users = Users(MongoUserReads(lambda: database))
    if case.startswith("token"):
        key = "fixture-legacy-token" if case.endswith("found") else "fixture-missing"
        response = token_user_response(users.by_token(key))
    else:
        key = "fixture-user-reads" if case.endswith("found") else "fixture-missing"
        response = public_user_response(users.public_by_id(key))
    expected = json.loads((ROOT / "contracts/user-read" / (case + ".json")).read_text())
    actual = {"status": response.status, "contentType": response.headers["Content-Type"],
              "body": json.loads(response.body)}
    assert comparison.compare(expected, actual)["match"]
    stored = database.user.find_one({"_id": original["_id"]})
    assert stored == {**original, "createTime": original["createTime"].replace(tzinfo=None),
                      "updateTime": original["updateTime"].replace(tzinfo=None)}
    assert database.user.count_documents({}) == 1


@pytest.mark.parametrize("explicit_null", [False, True])
def test_missing_and_explicit_null_match_java_baseline_without_database_writes(database, explicit_null):
    raw = {"_id": "fixture-defaults", "token": "fixture-token"}
    if explicit_null:
        raw.update(dict.fromkeys(["createTime", "updateTime", "subscriberCount", "videoCount"]))
    database.user.insert_one(raw.copy())
    fixed = datetime(2024, 1, 2, 3, 4, 5, tzinfo=UTC)
    user = MongoUserReads(lambda: database, clock=lambda: fixed).find_by_token("fixture-token")
    assert user.create_time == (None if explicit_null else fixed)
    assert user.update_time == (None if explicit_null else fixed)
    assert user.subscriber_count == (None if explicit_null else 0)
    assert user.video_count == (None if explicit_null else 0)
    assert user.phone is None
    assert database.user.find_one() == raw


def test_object_id_precedence_and_no_string_fallback_match_java(database):
    hex_id = "0123456789abcdef01234567"
    database.user.insert_many([{"_id": hex_id, "token": "fixture-string-id"},
                               {"_id": ObjectId(hex_id), "token": "fixture-object-id"}])
    reads = MongoUserReads(lambda: database)
    assert reads.find_by_id(hex_id).token == "fixture-object-id"
    assert reads.find_by_id(hex_id).id == hex_id
    assert reads.find_by_token("fixture-string-id").id == hex_id
    database.user.delete_one({"_id": ObjectId(hex_id)})
    assert reads.find_by_id(hex_id) is None
    assert reads.find_by_token("fixture-string-id") is not None


def test_numeric_conversion_and_type_sensitive_token_match_java(database):
    fixed = datetime(2024, 1, 2, 3, 4, 5, tzinfo=UTC)
    database.user.insert_many([
        {"_id": "fixture-numbers", "token": "fixture-token", "phone": 0,
         "subscriberCount": 12.75, "videoCount": "7", "createTime": fixed, "updateTime": fixed},
        {"_id": "fixture-numeric-token", "token": 1234},
    ])
    reads = MongoUserReads(lambda: database)
    user = reads.find_by_token("fixture-token")
    assert (user.phone, user.subscriber_count, user.video_count, user.create_time) == ("0", 12, 7, fixed)
    assert reads.find_by_token("1234") is None
    assert reads.find_by_id("fixture-numeric-token").token == "1234"


@pytest.mark.parametrize("case", ["profile-signed", "profile-fallback", "channel-signed", "channel-fallback"])
def test_real_mongo_user_reads_compose_with_profile_ports_and_java_http_goldens(database, case):
    original = {**golden_document(), "avatarFileId": "fixture-avatar"}
    database.user.insert_one(original.copy())

    class AvatarPorts:
        def key_for_file(self, file_id):
            assert file_id == "fixture-avatar"
            return "fixture-avatar-key" if case.endswith("signed") else None

        def sign(self, key, validity_seconds):
            assert key == "fixture-avatar-key"
            assert validity_seconds == 86400
            return "https://example.invalid/signed-avatar.png"

    avatars = AvatarPorts()
    profiles = Profiles(MongoUserReads(lambda: database), avatars, avatars)
    response = (public_user_response(profiles.mine(original["_id"])) if case.startswith("profile")
                else channel_response(profiles.channel(original["_id"])))
    expected = json.loads((ROOT / "contracts/profile-read" / (case + ".json")).read_text())
    actual = {"status": response.status, "contentType": response.headers["Content-Type"],
              "body": json.loads(response.body)}
    assert comparison.compare(expected, actual)["match"]
    stored = database.user.find_one()
    assert stored["token"] == original["token"]
    assert stored["avatarUrl"] == original["avatarUrl"]
    assert database.user.count_documents({}) == 1


def test_provider_error_is_propagated_and_initialization_is_lazy():
    calls = []

    def unavailable():
        calls.append(True)
        raise RuntimeError("fixture-unavailable")

    users = Users(MongoUserReads(unavailable))
    assert calls == []
    assert users.by_token(None) is None
    assert calls == []
    with pytest.raises(RuntimeError, match="fixture-unavailable"):
        users.by_token("fixture-token")


def test_unverified_bson_is_an_explicit_mapping_error_without_value_disclosure(database):
    database.user.insert_one({"_id": "fixture-unverified", "token": "fixture-token",
                              "subscriberCount": {"fixture-private-value": "do-not-log"}})
    with pytest.raises(UserMappingError) as error:
        MongoUserReads(lambda: database).find_by_token("fixture-token")
    assert "do-not-log" not in str(error.value)
    assert "fixture-private-value" not in str(error.value)
