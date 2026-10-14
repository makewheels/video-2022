"""Optional Web-function entry: uvicorn video_serverless.web:app --port 9000."""

from fastapi import FastAPI, Request
from starlette.concurrency import run_in_threadpool
from starlette.responses import Response

from video_serverless.application import dispatch
from video_serverless.http import Request as ApiRequest

app = FastAPI(docs_url=None, redoc_url=None, openapi_url=None)


@app.api_route("/{path:path}", methods=["GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"])
async def receive(request: Request):
    api_request = ApiRequest(
        method=request.method,
        path=request.url.path,
        headers={key.lower(): val for key, val in request.headers.items()},
        query=dict(request.query_params),
        body=await request.body(),
    )
    # PyMongo sync access must not block the ASGI event loop.
    result = await run_in_threadpool(dispatch, api_request)
    return Response(content=result.body, status_code=result.status, headers=result.headers)
