import json
from datetime import UTC, datetime

import pytest
from bson import Decimal128, Int64, ObjectId
from test_user_mongo_reads import isolated_database as isolated_database

from video_serverless.adapters.mongo_login import MongoLoginStore
from video_serverless.adapters.mongo_users import MongoUserReads
from video_serverless.business.login import Login
from video_serverless.user_responses import login_response

NOW = datetime(2026, 1, 1, tzinfo=UTC)
PHONE = "00000000000"


@pytest.fixture
def setup(isolated_database):
    database = isolated_database
    for name in ("verification_codes", "user", "wallet"):
        database[name].delete_many({})
    store = MongoLoginStore(lambda: database)
    return database, store


def login(store, production=True):
    return Login(store, lambda: "u_fixture-login", lambda: "fixture-new-token", lambda: NOW, production)


@pytest.mark.parametrize("production,code,exists,error", [
    (True, "1234", False, 12), (False, "111", False, 12),
    (True, "9999", True, 11), (True, "111", True, 11),
])
def test_rejected_login_has_legacy_response_and_no_writes(setup, production, code, exists, error):
    database, store = setup
    if exists:
        database.verification_codes.insert_one({"phone": PHONE, "code": "1234", "createdAt": NOW})
    result = login(store, production).submit(PHONE, code)
    response = login_response(result)
    assert response.status == 200
    assert json.loads(response.body) == {
        "code": error, "message": "验证码错误" if error == 11 else "验证码已过期", "data": None,
    }
    assert database.verification_codes.count_documents({}) == int(exists)
    assert database.user.count_documents({}) == database.wallet.count_documents({}) == 0


@pytest.mark.parametrize("production,code", [(True, "1234"), (False, "111")])
def test_new_user_and_decimal_wallet_are_saved_after_code_consumption(setup, production, code):
    database, store = setup
    database.verification_codes.insert_one({"phone": PHONE, "code": "1234", "createdAt": NOW})
    result = login(store, production).submit(PHONE, code)
    assert result.error_code is None
    assert result.user.phone == PHONE
    assert result.user.token == "fixture-new-token"
    assert database.verification_codes.count_documents({}) == 0
    assert MongoUserReads(lambda: database).find_by_token(result.user.token) == result.user
    wallet = database.wallet.find_one({"userId": result.user.id})
    assert isinstance(wallet["_id"], ObjectId)
    assert wallet["balance"] == Decimal128("0")
    assert "_class" not in wallet
    document = database.user.find_one()
    assert "_class" not in document
    assert isinstance(document["subscriberCount"], Int64)
    assert json.loads(login_response(result).body)["data"]["subscriberCount"] == 0


def test_existing_login_rotates_token_without_touching_profile_dates_or_wallet(setup):
    database, store = setup
    old = datetime(2024, 1, 1, tzinfo=UTC)
    database.user.insert_one({"_id": "fixture-user", "phone": PHONE, "token": "fixture-old-token",
                              "nickname": "keep", "createTime": old, "updateTime": old})
    database.wallet.insert_one({"userId": "fixture-user", "balance": Decimal128("12.34")})
    wallet = database.wallet.find_one()
    database.verification_codes.insert_one({"phone": PHONE, "code": "1234"})
    result = login(store).submit(PHONE, "1234")
    assert result.user.id == "fixture-user"
    assert result.user.nickname == "keep"
    assert result.user.update_time == old
    assert database.wallet.find_one() == wallet
    assert database.wallet.count_documents({}) == 1
    reads = MongoUserReads(lambda: database)
    assert reads.find_by_token("fixture-old-token") is None
    assert reads.find_by_token("fixture-new-token") == result.user


@pytest.mark.parametrize("failure", ["save_user", "ensure_wallet"])
def test_partial_failure_propagates_and_keeps_legacy_write_order(setup, failure):
    database, store = setup
    database.verification_codes.insert_one({"phone": PHONE, "code": "1234"})

    def fail(*args):
        raise RuntimeError("fixture persistence failure")

    setattr(store, failure, fail)
    with pytest.raises(RuntimeError, match="fixture persistence failure"):
        login(store).submit(PHONE, "1234")
    assert database.verification_codes.count_documents({}) == 0
    assert database.user.count_documents({}) == int(failure == "ensure_wallet")
    assert database.wallet.count_documents({}) == 0
    assert login(store).submit(PHONE, "1234").error_code == 12
