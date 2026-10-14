import pytest
from starlette.applications import Starlette
from starlette.requests import Request
from starlette.responses import JSONResponse
from starlette.routing import Route
from starlette.testclient import TestClient
from test_user_reads import Reads

from video_serverless.adapters.asgi_metadata import MetadataMappingError, from_asgi_metadata
from video_serverless.business.request_identity import IdentityResolver


def test_actual_asgi_request_preserves_duplicate_headers_parameters_and_original_query():
    captured = []

    async def endpoint(request):
        metadata = from_asgi_metadata(request)
        captured.append(metadata)
        identity = IdentityResolver(Reads()).resolve(metadata)
        return JSONResponse({"userId": identity.user_id})

    app = Starlette(routes=[Route("/{path:path}", endpoint, methods=["GET", "POST"])])
    with TestClient(app) as client:
        response = client.get(
            "/user/getMyProfile?token=fixture-wrong&token=fixture-legacy-token&value=a%2Bb+z&blank=",
            headers=[("ToKeN", "fixture-legacy-token"), ("token", "fixture-second"),
                     ("User-Agent", "fixture-first-agent"), ("user-agent", "fixture-second-agent"),
                     ("X-Forwarded-For", "198.51.100.20")],
        )
        assert response.json()["userId"] == "fixture-user-reads"
    metadata = captured[0]
    assert [value for name, value in metadata.headers if name.lower() == "token"] == [
        "fixture-legacy-token", "fixture-second",
    ]
    assert metadata.first_header("USER-AGENT") == "fixture-first-agent"
    assert metadata.first_parameter("token") == "fixture-wrong"
    assert metadata.parameters[-2:] == (("value", "a+b z"), ("blank", ""))
    assert metadata.raw_query == "token=fixture-wrong&token=fixture-legacy-token&value=a%2Bb+z&blank="
    assert metadata.path == "/user/getMyProfile"
    assert metadata.url == "http://testserver/user/getMyProfile"
    assert metadata.remote_addr == "testclient"


def scope(**extra):
    return {"type": "http", "http_version": "1.1", "scheme": "http", "method": "GET",
            "path": "/fixture%2Fpath", "raw_path": b"/fixture%252Fpath", "query_string": b"",
            "headers": [(b"host", b"fixture.invalid")], "client": ("192.0.2.10", 12345),
            "server": ("fixture.invalid", 80), **extra}


def test_raw_path_is_not_decoded_again_and_peer_is_not_invented_from_forwarding_headers():
    metadata = from_asgi_metadata(Request(scope(headers=[(b"host", b"fixture.invalid"),
                                                        (b"x-forwarded-for", b"198.51.100.20")])))
    assert metadata.path == "/fixture%252Fpath"
    assert metadata.url == "http://fixture.invalid/fixture%252Fpath"
    assert metadata.raw_query is None
    assert metadata.remote_addr == "192.0.2.10"


@pytest.mark.parametrize("extra", [{"raw_path": None}, {"client": None}, {"raw_path": b"/\xff"}])
def test_unavailable_or_unverified_wire_metadata_fails_without_fabricating_values(extra):
    with pytest.raises(MetadataMappingError) as error:
        from_asgi_metadata(Request(scope(**extra)))
    assert "fixture" not in str(error.value)


@pytest.mark.parametrize("content_type", [
    "application/x-www-form-urlencoded", "multipart/form-data; boundary=fixture-boundary",
])
def test_form_parameter_merge_is_not_silently_approximated(content_type):
    request = Request(scope(method="POST", headers=[(b"host", b"fixture.invalid"),
                                                   (b"content-type", content_type.encode())]))
    with pytest.raises(MetadataMappingError, match="form parameter merging"):
        from_asgi_metadata(request)


def test_json_post_query_metadata_does_not_consume_the_body():
    async def not_received():
        raise AssertionError("Metadata extraction must not consume the request body")

    request = Request(scope(method="POST", query_string=b"token=fixture-query", headers=[
        (b"host", b"fixture.invalid"), (b"content-type", b"application/json"),
    ]), receive=not_received)
    metadata = from_asgi_metadata(request)
    assert metadata.method == "POST"
    assert metadata.first_parameter("token") == "fixture-query"
