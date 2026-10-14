from datetime import UTC, datetime

import pytest
from test_user_mongo_reads import isolated_database as isolated_database

from video_serverless.adapters.mongo_profile_updates import MongoProfileWrites
from video_serverless.business.profile_updates import ProfileUpdates
from video_serverless.profile_json import ProfileBindingError, decode_profile


@pytest.mark.parametrize("body,nickname,bio", [
    (b'{}', None, None),
    (b'{"nickname":null,"bio":null}', None, None),
    (b'{"nickname":123,"bio":true,"ignored":{"nested":1}}', "123", "true"),
    (b'{"nickname":1e2,"bio":-0.50}', "1e2", "-0.50"),
    (b'{"nickname":1E+2,"bio":-0}', "1E+2", "-0"),
    (b'{"nickname":"first","nickname":"last"}', "last", None),
    (b' \r\n{"nickname":"first"}\t ', "first", None),
])
def test_decode_then_actual_mongo_update_matches_java_binding(isolated_database, body, nickname, bio):
    database = isolated_database
    database.user.delete_many({})
    database.user.insert_one({"_id": "fixture-json-user", "nickname": "original", "bio": "original",
                              "token": "fixture-json-token"})
    request = decode_profile(body)
    assert (request.nickname, request.bio) == (nickname, bio)
    updates = ProfileUpdates(MongoProfileWrites(lambda: database), lambda: datetime(2025, 1, 1, tzinfo=UTC))
    assert updates.update("fixture-json-user", request.nickname, request.bio).error is None
    stored = database.user.find_one()
    assert stored["nickname"] == nickname
    assert stored["bio"] == bio
    assert stored["token"] == "fixture-json-token"
    assert database.user.count_documents({}) == 1


@pytest.mark.parametrize("body", [b'{"nickname":[]}', b'{"bio":{}}', b'[]', b'"text"', b'null', b'', b'{',
                                  b'{"nickname":[],"nickname":"valid"}',
                                  b'{"nickname":"first"}{}', b'{"nickname":"first"} trailing'])
def test_invalid_shapes_fail_without_exposing_request_contents(body):
    with pytest.raises(ProfileBindingError) as error:
        decode_profile(body)
    assert str(error.value) == "Profile JSON binding failed"


def test_duplicate_scalar_is_bound_before_final_length_validation():
    request = decode_profile(b'{"nickname":"' + b'x' * 31 + b'","nickname":"last"}')
    assert request.nickname == "last"
    assert request.bio is None
