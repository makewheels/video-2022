from dataclasses import replace
from urllib.parse import parse_qs, urlsplit

import pytest

from video_serverless.adapters.hmac_access_signatures import HmacAccessSignatures
from video_serverless.business.playback_authorization import PlaybackAuthorization, PlaybackContext, PlaybackVideo
from video_serverless.ports.playback_signatures import AccessClaim

NOW = 1700000000000
CLAIM = AccessClaim("v_fixture", "client_fixture", "session_fixture", "720p", "file_fixture", str(NOW), "nonce_fixture")


@pytest.mark.parametrize("claim,expected", [
    (CLAIM, "dd45f47165995099d0186d5dec6c4b0f3274490b1211d6b12d319875d39f75e6"),
    (AccessClaim(None, "", None, None, "playlist", str(NOW), "固定"),
     "b63dda6c6d6ddce9d0f036672677ca7aa019e31ae22cadc165d0e1782e1a04d6"),
    (AccessClaim("v_fixture", "\ud800", "session_fixture", "", "playlist", str(NOW), "nonce"),
     "f4c72384dcc2b3ccbee40cb97e337811ff21c29e073d53b7b046c13b2bd1f94d"),
])
def test_signature_matches_actual_current_java_service_source(claim, expected):
    assert HmacAccessSignatures("fixture-secret", lambda: NOW).sign(claim) == expected


@pytest.mark.parametrize("field", ["video_id", "client_id", "session_id", "resolution", "file_id",
                                   "timestamp", "nonce"])
def test_every_claim_field_is_bound_and_cannot_be_substituted(field):
    signatures = HmacAccessSignatures("fixture-secret", lambda: NOW)
    signature = signatures.sign(CLAIM)
    assert signatures.verify(CLAIM, signature)
    assert not signatures.verify(replace(CLAIM, **{field: "fixture-substitution"}), signature)


@pytest.mark.parametrize("delta,valid", [(-300001, False), (-300000, True), (0, True), (300000, True), (300001, False)])
def test_legacy_time_window_is_symmetric_and_inclusive(delta, valid):
    assert HmacAccessSignatures("fixture-secret", lambda: NOW).timestamp_valid(str(NOW + delta)) is valid


@pytest.mark.parametrize("value", [None, "", " 1700000000000", "1_700000000000", "9223372036854775808"])
def test_timestamp_uses_java_long_lexical_rules(value):
    assert not HmacAccessSignatures("fixture-secret", lambda: NOW).timestamp_valid(value)


def test_blank_signature_short_circuits_even_when_secret_is_invalid():
    signatures = HmacAccessSignatures("", lambda: NOW)
    for value in (None, "", " \t\u2003"):
        assert not signatures.verify(CLAIM, value)
    with pytest.raises(RuntimeError, match="Failed to compute"):
        signatures.sign(CLAIM)


def test_private_owner_or_bound_short_lived_playlist_authorization_matches_source():
    signatures = HmacAccessSignatures("fixture-secret", lambda: NOW)
    authorization = PlaybackAuthorization(signatures, lambda: NOW, lambda: "fixture-nonce")
    context = PlaybackContext("v_fixture", "client_fixture", "session_fixture")
    private = PlaybackVideo("fixture-owner", "PRIVATE")
    assert authorization.authorize(None, context, None) == 22
    assert authorization.authorize(PlaybackVideo(None, "PUBLIC"), context, None) is None
    assert authorization.authorize(private, context, "fixture-owner") is None
    assert authorization.authorize(private, context, None) == 35
    assert authorization.authorize(PlaybackVideo(None, "PRIVATE"), context, None) == 35
    url = authorization.sign_url("https://example.invalid/playlist?videoId=v_fixture", context)
    query = parse_qs(urlsplit(url).query)
    args = (query["playbackTime"][0], query["playbackNonce"][0], query["playbackSign"][0])
    assert authorization.authorize(private, context, None, *args) is None
    assert authorization.authorize(private, replace(context, video_id="other-video"), None, *args) == 35
    assert authorization.authorize(private, replace(context, client_id="other-client"), None, *args) == 35
    assert authorization.authorize(private, context, None, str(NOW - 300001), *args[1:]) == 35
