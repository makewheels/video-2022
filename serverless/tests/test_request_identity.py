from concurrent.futures import ThreadPoolExecutor
from dataclasses import FrozenInstanceError

import pytest
from test_user_reads import Reads

from video_serverless.business.request_identity import IdentityResolver, select_token
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.ports.users import UserSnapshot


def metadata(headers=(), parameters=(), **extra):
    return RequestMetadata("GET", "/user/getMyProfile", "http://fixture.invalid/user/getMyProfile",
                           "192.0.2.10", headers, parameters, **extra)


@pytest.mark.parametrize("headers,parameters,expected", [
    ((), (), None),
    ((("ToKeN", "fixture-header"),), (("token", "fixture-query"),), "fixture-header"),
    ((("token", "fixture-first"), ("TOKEN", "fixture-second")), (), "fixture-first"),
    ((("token", ""),), (("token", "fixture-query"),), "fixture-query"),
    ((("token", " "),), (("token", "fixture-query"),), " "),
    ((), (("token", "fixture-wrong"), ("token", "fixture-valid")), "fixture-wrong"),
    ((), (("Token", "fixture-wrong-case"),), None),
    ((("token", ""),), (), ""),
    ((), (("token", ""),), ""),
    ((("token", "Bearer fixture-value"),), (), "Bearer fixture-value"),
])
def test_selection_keeps_legacy_header_first_and_parameter_first_semantics(headers, parameters, expected):
    assert select_token(metadata(headers, parameters)) == expected


def test_missing_token_avoids_lookup_but_empty_header_still_queries_empty_stored_token():
    reads = Reads(UserSnapshot(id="fixture-empty", token=""))
    resolver = IdentityResolver(reads)
    assert resolver.resolve(metadata()).user_id is None
    assert reads.calls == []
    identity = resolver.resolve(metadata((("token", ""),)))
    assert identity.user_id == "fixture-empty"
    assert identity.token == ""
    assert reads.calls == [("token", "")]


def test_metadata_snapshots_duplicate_sequences_and_retains_raw_query_without_reencoding():
    headers = [["User-Agent", "fixture-first"], ["user-agent", "fixture-second"]]
    parameters = [["token", "fixture-first"], ["token", "fixture-second"]]
    request = metadata(headers, parameters, raw_query="token=fixture%2Bfirst&token=fixture+second")
    headers[0][1] = "changed"
    parameters.clear()
    assert request.first_header("USER-AGENT") == "fixture-first"
    assert request.first_parameter("token") == "fixture-first"
    assert len(request.headers) == 2 and len(request.parameters) == 2
    assert request.raw_query == "token=fixture%2Bfirst&token=fixture+second"
    with pytest.raises(FrozenInstanceError):
        request.remote_addr = "changed"


def test_metadata_and_identity_repr_do_not_print_raw_credentials_or_query():
    reads = Reads()
    request = metadata((("token", "fixture-legacy-token"),), raw_query="token=fixture-private-query")
    identity = IdentityResolver(reads).resolve(request)
    assert identity.user_id == reads.snapshot.id
    for displayed in (repr(request), repr(identity)):
        assert "fixture-legacy-token" not in displayed
        assert "fixture-private-query" not in displayed
    assert request.first_header("token") == "fixture-legacy-token"
    assert identity.user.token == "fixture-legacy-token"


def test_provider_failure_propagates_without_a_cached_identity_on_the_next_request():
    class Intermittent:
        def find_by_token(self, token):
            if token == "fixture-failure":
                raise RuntimeError("fixture-unavailable")
            return UserSnapshot(id="fixture-next", token=token)

    resolver = IdentityResolver(Intermittent())
    with pytest.raises(RuntimeError, match="fixture-unavailable"):
        resolver.resolve(metadata((("token", "fixture-failure"),)))
    assert resolver.resolve(metadata((("token", "fixture-next-token"),))).user_id == "fixture-next"
    assert resolver.resolve(metadata()).user is None


def test_concurrent_requests_share_resolver_but_do_not_share_identity_state():
    class Echo:
        def find_by_token(self, token):
            return UserSnapshot(id=token, token=token)

    resolver = IdentityResolver(Echo())
    tokens = ["fixture-context-" + str(index) for index in range(40)]
    with ThreadPoolExecutor(max_workers=4) as pool:
        identities = list(pool.map(lambda token: resolver.resolve(metadata((("token", token),))), tokens))
    assert [identity.user_id for identity in identities] == tokens
    assert [identity.token for identity in identities] == tokens
    assert resolver.resolve(metadata()).user_id is None
