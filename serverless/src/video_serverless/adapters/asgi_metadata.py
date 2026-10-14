"""ASGI metadata capability only; not connected to the existing prototype entrypoint."""

from starlette.requests import Request

from video_serverless.ports.request_identity import RequestMetadata


class MetadataMappingError(ValueError):
    """A protocol gap, without request values in the error message."""


def from_asgi_metadata(request: Request) -> RequestMetadata:
    raw_path = request.scope.get("raw_path")
    if raw_path is None or request.client is None:
        raise MetadataMappingError("Original URI and peer address are required for legacy metadata")
    try:
        path = raw_path.decode("ascii")
    except UnicodeDecodeError:
        raise MetadataMappingError("Non-ASCII raw URI compatibility has not been verified") from None
    headers = tuple((name.decode("latin-1"), value.decode("latin-1"))
                    for name, value in request.scope.get("headers", ()))
    raw_query = request.scope.get("query_string", b"")
    metadata = RequestMetadata(
        method=request.method, path=path,
        url=str(request.url.replace(path=path, query="")),
        remote_addr=request.client.host,
        headers=headers, parameters=tuple(request.query_params.multi_items()),
        raw_query=raw_query.decode("latin-1") if raw_query else None,
    )
    content_type = (metadata.first_header("content-type") or "").split(";", 1)[0].strip().lower()
    if request.method == "POST" and content_type in {"application/x-www-form-urlencoded", "multipart/form-data"}:
        raise MetadataMappingError("Servlet form parameter merging has not been verified")
    return metadata
