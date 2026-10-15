from datetime import UTC, datetime

import pytest
from bson import ObjectId
from starlette.applications import Starlette
from starlette.responses import Response
from starlette.routing import Route
from starlette.testclient import TestClient
from test_user_mongo_reads import isolated_database as isolated_database

from video_serverless.adapters.asgi_metadata import from_asgi_metadata
from video_serverless.adapters.mongo_request_journal import MongoRequestJournal
from video_serverless.adapters.mongo_users import MongoUserReads
from video_serverless.adapters.mongo_watching_ids import MongoWatchingIds
from video_serverless.business.request_identity import IdentityResolver
from video_serverless.business.watching_ids import WatchingIds
from video_serverless.request_cycle import RequestCycle
from video_serverless.user_responses import client_id_response, session_id_response


@pytest.mark.parametrize("kind,field", [("client", "clientId"), ("session", "sessionId")])
def test_unprotected_http_allocates_and_logs_each_request_with_actual_peer(isolated_database, kind, field):
    database = isolated_database
    database[kind].delete_many({})
    database.requestLog.delete_many({})
    ids = WatchingIds(MongoWatchingIds(lambda: database), lambda: datetime(2026, 1, 1, tzinfo=UTC))
    cycle = RequestCycle(IdentityResolver(MongoUserReads(lambda: database)),
                         MongoRequestJournal(lambda: database), lambda: 1000)
    encode = client_id_response if kind == "client" else session_id_response

    def endpoint(request):
        metadata = from_asgi_metadata(request)

        def handle(metadata, identity):
            value = getattr(ids, kind)(metadata.remote_addr, metadata.first_header("User-Agent"))
            return encode(value)

        result = cycle.run(metadata, handle)
        return Response(result.response.body, status_code=result.response.status, headers=result.response.headers)

    path = "/" + kind + "/request" + kind.capitalize() + "Id"
    with TestClient(Starlette(routes=[Route(path, endpoint)])) as client:
        first = client.get(path, headers={"User-Agent": "fixture-agent", "X-Forwarded-For": "192.0.2.1"})
        second = client.get(path)
    assert first.status_code == second.status_code == 200
    assert first.headers["content-type"] == "application/json"
    assert first.json()["code"] == 0 and first.json()["message"] == "success"
    first_id = first.json()["data"][field]
    assert first_id != second.json()["data"][field]
    stored = database[kind].find_one({"_id": ObjectId(first_id)})
    assert stored["ip"] == "testclient"
    assert stored["userAgent"] == "fixture-agent"
    assert database[kind].count_documents({}) == 2
    assert database.requestLog.count_documents({}) == 2
