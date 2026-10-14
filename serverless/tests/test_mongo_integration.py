"""Opt-in read-only test; cannot pass without an actual database connection."""

import os

import pytest

from video_serverless.adapters.mongo import MongoPublicVideoProbe


@pytest.mark.skipif(
    not (os.environ.get("MONGODB_URI") and os.environ.get("MONGODB_DATABASE")),
    reason="Actual MongoDB connection has not been configured",
)
def test_actual_mongo_query():
    probe = MongoPublicVideoProbe(os.environ["MONGODB_URI"], os.environ["MONGODB_DATABASE"])
    try:
        result = probe.read_public_video()
        assert isinstance(result.found_public_video, bool)
        assert result.client_setup_ms >= 0
        assert result.read_ms >= 0
    finally:
        if probe._client is not None:
            probe._client.close()
