import json
from dataclasses import replace

import pytest
from test_user_reads import ROOT, Reads, comparison, user

from video_serverless.application import Application
from video_serverless.business.profiles import Profiles
from video_serverless.business.prototype import Prototype
from video_serverless.http import Request
from video_serverless.user_responses import channel_response, public_user_response


class Avatars:
    def __init__(self, key="fixture-avatar-key", url="https://example.invalid/signed-avatar.png"):
        self.key = key
        self.url = url
        self.lookups = []
        self.signatures = []

    def key_for_file(self, file_id):
        self.lookups.append(file_id)
        return self.key

    def sign(self, key, validity_seconds):
        self.signatures.append((key, validity_seconds))
        return self.url


@pytest.mark.parametrize("case", [
    "profile-signed", "profile-fallback", "channel-signed", "channel-fallback", "channel-missing",
])
def test_profile_and_channel_payloads_match_actual_java_http_goldens(case):
    reads = Reads(replace(user(), avatar_file_id="fixture-avatar"))
    avatars = Avatars(key=None if case.endswith("fallback") else "fixture-avatar-key")
    profiles = Profiles(reads, avatars, avatars)
    if case.startswith("profile"):
        response = public_user_response(profiles.mine(reads.snapshot.id))
    else:
        key = "fixture-missing" if case.endswith("missing") else reads.snapshot.id
        response = channel_response(profiles.channel(key))
    expected = json.loads((ROOT / "contracts/profile-read" / (case + ".json")).read_text())
    actual = {"status": response.status, "contentType": response.headers["Content-Type"],
              "body": json.loads(response.body)}
    assert comparison.compare(expected, actual)["match"]
    assert reads.snapshot.token == "fixture-legacy-token"
    assert reads.snapshot.avatar_url == "https://example.invalid/avatar.png"
    assert avatars.signatures == ([("fixture-avatar-key", 86400)] if case.endswith("signed") else [])


@pytest.mark.parametrize("file_id,key,expected_lookups", [
    (None, "unused", []), ("", "unused", []),
    ("fixture-avatar", None, ["fixture-avatar"]), ("fixture-avatar", "", ["fixture-avatar"]),
])
def test_legacy_avatar_fallback_preserves_original_url_and_avoids_unneeded_signing(file_id, key, expected_lookups):
    reads = Reads(replace(user(), avatar_file_id=file_id))
    avatars = Avatars(key=key)
    profile = Profiles(reads, avatars, avatars).mine(reads.snapshot.id)
    assert profile.avatar_url == reads.snapshot.avatar_url
    assert profile.token is None
    assert avatars.lookups == expected_lookups
    assert avatars.signatures == []


def test_channel_only_defaults_null_counts_and_subscription_is_always_false():
    reads = Reads(replace(user(), subscriber_count=None, video_count=-7))
    avatars = Avatars()
    profiles = Profiles(reads, avatars, avatars)
    channel = profiles.channel(reads.snapshot.id)
    assert (channel.subscriber_count, channel.video_count, channel.is_subscribed) == (0, -7, False)
    assert profiles.mine(reads.snapshot.id).subscriber_count is None
    assert avatars.lookups == []
    assert avatars.signatures == []


def test_signer_returning_null_is_not_silently_replaced_by_legacy_url():
    reads = Reads(replace(user(), avatar_file_id="fixture-avatar"))
    avatars = Avatars(url=None)
    assert Profiles(reads, avatars, avatars).mine(reads.snapshot.id).avatar_url is None


def test_missing_profile_avoids_avatar_io_and_provider_failure_remains_visible():
    reads = Reads()
    avatars = Avatars()
    profiles = Profiles(reads, avatars, avatars)
    assert profiles.mine("fixture-missing") is None
    assert profiles.channel("fixture-missing") is None
    assert avatars.lookups == []

    class Unavailable:
        def key_for_file(self, file_id):
            raise RuntimeError("fixture-unavailable")

    with pytest.raises(RuntimeError, match="fixture-unavailable"):
        Profiles(Reads(replace(user(), avatar_file_id="fixture-avatar")), Unavailable(), avatars).mine(user().id)


def test_new_profile_capabilities_do_not_expose_incomplete_business_routes():
    app = Application(Prototype(None, lambda: 0), "fixture-prototype-token")
    for path in ["/user/getMyProfile", "/user/getChannel"]:
        assert app.dispatch(Request("GET", path)).status == 404
