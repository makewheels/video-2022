import importlib.util
import json
from dataclasses import FrozenInstanceError
from datetime import UTC, datetime, timedelta, timezone
from pathlib import Path

import pytest

from video_serverless.application import Application
from video_serverless.business.prototype import Prototype
from video_serverless.business.users import Users
from video_serverless.http import Request
from video_serverless.ports.users import UserSnapshot
from video_serverless.user_responses import public_user_response, token_user_response

ROOT = Path(__file__).parents[1]
SPEC = importlib.util.spec_from_file_location("user_comparison", ROOT / "contracts/compare_responses.py")
comparison = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(comparison)


def user():
    return UserSnapshot(
        id="fixture-user-reads", phone="00000000000", register_channel="TEST",
        create_time=datetime(2024, 1, 2, 3, 4, 5, tzinfo=UTC),
        update_time=datetime(2024, 1, 2, 3, 4, 5, 678000, tzinfo=UTC),
        token="fixture-legacy-token", nickname="测试读取", avatar_url="https://example.invalid/avatar.png",
        subscriber_count=12, video_count=7,
    )


class Reads:
    def __init__(self, snapshot=None):
        self.snapshot = snapshot or user()
        self.calls = []

    def find_by_token(self, token):
        self.calls.append(("token", token))
        return self.snapshot if token == self.snapshot.token else None

    def find_by_id(self, user_id):
        self.calls.append(("id", user_id))
        return self.snapshot if user_id == self.snapshot.id else None


@pytest.mark.parametrize("case", ["token-found", "token-missing", "id-found", "id-missing"])
def test_python_read_payload_matches_actual_java_http_fixture(case):
    reads = Reads()
    users = Users(reads)
    if case.startswith("token"):
        token = "fixture-legacy-token" if case.endswith("found") else "fixture-missing"
        response = token_user_response(users.by_token(token))
    else:
        user_id = "fixture-user-reads" if case.endswith("found") else "fixture-missing"
        response = public_user_response(users.public_by_id(user_id))
    expected = json.loads((ROOT / "contracts/user-read" / (case + ".json")).read_text())
    actual = {"status": response.status, "contentType": response.headers["Content-Type"],
              "body": json.loads(response.body)}
    assert comparison.compare(expected, actual) == {"match": True, "differences": [], "limitReached": False}


def test_public_snapshot_redaction_does_not_change_stored_token_or_other_fields():
    reads = Reads()
    original = reads.snapshot
    public = Users(reads).public_by_id(original.id)
    assert public.token is None
    assert reads.snapshot is original
    assert original.token == "fixture-legacy-token"
    assert public.phone == original.phone
    assert public.avatar_url == original.avatar_url
    assert public.subscriber_count == original.subscriber_count
    assert Users(reads).by_token(original.token) is original
    with pytest.raises(FrozenInstanceError):
        public.token = "replacement"


def test_token_is_not_normalized_and_none_does_not_query_the_store():
    reads = Reads()
    users = Users(reads)
    assert users.by_token(None) is None
    assert reads.calls == []
    for value in ["", " fixture-legacy-token ", "Bearer fixture-legacy-token"]:
        assert users.by_token(value) is None
        assert reads.calls[-1] == ("token", value)
    empty = UserSnapshot(id="fixture-empty-token", token="")
    assert Users(Reads(empty)).by_token("") is empty


def test_provider_failure_is_not_misreported_as_invalid_token():
    class Unavailable:
        def find_by_token(self, token):
            raise RuntimeError("fixture-store-unavailable")
    with pytest.raises(RuntimeError, match="fixture-store-unavailable"):
        Users(Unavailable()).by_token("fixture-legacy-token")


def test_timezone_aware_timestamp_is_utc_milliseconds_and_null_fields_remain():
    snapshot = UserSnapshot(
        id="fixture-time", create_time=datetime(2024, 1, 2, 11, 4, 5, 678999, tzinfo=timezone(timedelta(hours=8)))
    )
    data = json.loads(token_user_response(snapshot).body)["data"]
    assert data["createTime"] == "2024-01-02T03:04:05.678Z"
    assert data["updateTime"] is None
    assert data["subscriberCount"] is None
    assert len(data) == 13
    with pytest.raises(ValueError, match="explicit timezone"):
        token_user_response(UserSnapshot(id="fixture-naive", create_time=datetime(2024, 1, 2)))


def test_new_use_cases_are_not_exposed_as_partial_business_routes():
    app = Application(Prototype(None, lambda: 0), "fixture-prototype-token")
    for path in ["/user/getUserByToken", "/user/getUserById"]:
        assert app.dispatch(Request("GET", path, query={"token": "fixture-legacy-token"})).status == 404
