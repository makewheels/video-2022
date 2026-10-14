import json
from datetime import UTC, datetime

import pytest
from test_user_reads import ROOT, comparison

from video_serverless.business.profile_updates import ProfileUpdates
from video_serverless.user_responses import profile_update_response

FIXED = datetime(2024, 1, 2, 3, 4, 5, tzinfo=UTC)


class Writes:
    def __init__(self):
        self.calls = []

    def update_profile(self, *args):
        self.calls.append(args)


@pytest.mark.parametrize("case,nickname,bio", [
    ("success", "😀" * 15, " "),
    ("nickname-long", "😀" * 16, "new"),
    ("bio-long", "new", "字" * 201),
])
def test_update_result_matches_actual_java_http_golden(case, nickname, bio):
    writes = Writes()
    result = ProfileUpdates(writes, lambda: FIXED).update("fixture-user-reads", nickname, bio)
    response = profile_update_response(result)
    expected = json.loads((ROOT / "contracts/profile-update" / (case + ".json")).read_text())
    actual = {"status": response.status, "contentType": response.headers["Content-Type"],
              "body": json.loads(response.body)}
    assert comparison.compare(expected, actual)["match"]
    assert len(writes.calls) == (1 if case == "success" else 0)


@pytest.mark.parametrize("nickname,bio", [(None, None), ("", ""), (" ", " \n"), ("字" * 30, "字" * 200)])
def test_accepted_values_are_not_trimmed_and_both_fields_are_always_forwarded(nickname, bio):
    writes = Writes()
    result = ProfileUpdates(writes, lambda: FIXED).update("fixture-user", nickname, bio)
    assert result.error is None
    assert writes.calls == [("fixture-user", nickname, bio, FIXED)]


def test_nickname_validation_precedes_bio_and_rejection_does_not_read_clock_or_write():
    writes = Writes()

    def forbidden_clock():
        raise AssertionError("Rejected update must not acquire a timestamp")

    result = ProfileUpdates(writes, forbidden_clock).update("fixture-user", "字" * 31, "字" * 201)
    assert result.error == "昵称不能超过30字"
    assert writes.calls == []


def test_provider_failure_propagates_without_replaying_the_write():
    class Unavailable:
        calls = 0

        def update_profile(self, *args):
            self.calls += 1
            raise RuntimeError("fixture-store-unavailable")

    writes = Unavailable()
    with pytest.raises(RuntimeError, match="fixture-store-unavailable"):
        ProfileUpdates(writes, lambda: FIXED).update("fixture-user", None, None)
    assert writes.calls == 1
