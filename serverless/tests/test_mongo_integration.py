"""Opt-in read-only test; cannot pass without an actual database connection."""

import os

import pytest

from video_serverless.mongo import read_probe


@pytest.mark.skipif(
    not (os.environ.get("MONGODB_URI") and os.environ.get("MONGODB_DATABASE")),
    reason="Actual MongoDB connection has not been configured",
)
def test_actual_mongo_query():
    result = read_probe()
    assert isinstance(result["foundPublicVideo"], bool)
    assert result["clientSetupMs"] >= 0
    assert result["readMs"] >= 0
