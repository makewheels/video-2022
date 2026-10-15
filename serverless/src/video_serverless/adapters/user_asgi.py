"""Explicit ASGI factory for isolated user validation; stable prototype app is unchanged."""

from starlette.applications import Starlette
from starlette.concurrency import run_in_threadpool
from starlette.responses import Response
from starlette.routing import Route

from video_serverless.adapters.asgi_metadata import from_asgi_metadata


def create_user_asgi(application):
    async def receive(request):
        metadata = from_asgi_metadata(request)
        body = await request.body()
        result = await run_in_threadpool(application.dispatch, metadata, body)
        response = result.response
        return Response(response.body, status_code=response.status, headers=response.headers)

    return Starlette(routes=[Route("/{path:path}", receive,
                                  methods=["GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"])])
