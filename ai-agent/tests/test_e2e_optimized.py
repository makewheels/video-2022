"""E2E tests for optimized agent server."""

import asyncio
import json
import time

import pytest
from fastapi.testclient import TestClient

from video_agent.client import ModelClient
from video_agent.server_optimized import create_optimized_app
from video_agent.tools import VideoTools


@pytest.fixture
def test_client():
    """Create a test client with fixture backend."""
    client = ModelClient(
        model="MiniMax-M2.7",
        base_url="https://api.minimaxi.com/v1",
        api_key="test-key"
    )
    tools = VideoTools(backend="fixture")
    app = create_optimized_app(client, tools)
    return TestClient(app)


def test_health_endpoint(test_client):
    """Test health check endpoint."""
    response = test_client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert "mongodb_sessions" in data["features"]
    assert "context_management" in data["features"]
    assert "error_handling" in data["features"]


def test_tools_endpoint(test_client):
    """Test tools listing endpoint."""
    response = test_client.get("/tools")
    assert response.status_code == 200
    data = response.json()
    assert "tools" in data
    assert len(data["tools"]) > 0


def test_chat_stream_basic(test_client):
    """Test basic chat streaming."""
    response = test_client.post(
        "/chat/stream",
        json={"query": "搜索关键词'测试'的视频", "session_id": "test-session-1"}
    )
    assert response.status_code == 200
    assert response.headers["content-type"] == "text/event-stream; charset=utf-8"

    # Parse SSE events
    events = []
    for line in response.iter_lines():
        if line.startswith(b"data: "):
            data = line[6:].decode("utf-8")
            if data != "[DONE]":
                events.append(json.loads(data))

    # Should have tool_start, tool_call, and text events
    event_types = [e["type"] for e in events]
    assert "tool_start" in event_types
    assert "tool_call" in event_types


def test_chat_stream_with_session(test_client):
    """Test chat streaming with session persistence."""
    session_id = f"test-session-{int(time.time())}"

    # First message
    response1 = test_client.post(
        "/chat/stream",
        json={"query": "搜索视频", "session_id": session_id}
    )
    assert response1.status_code == 200

    # Second message in same session
    response2 = test_client.post(
        "/chat/stream",
        json={"query": "显示第一个视频的详情", "session_id": session_id}
    )
    assert response2.status_code == 200


def test_delete_session(test_client):
    """Test session deletion."""
    session_id = "test-delete-session"

    # Create session by sending a message
    test_client.post(
        "/chat/stream",
        json={"query": "测试", "session_id": session_id}
    )

    # Delete session
    response = test_client.delete(f"/sessions/{session_id}")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"


def test_cleanup_sessions(test_client):
    """Test session cleanup endpoint."""
    response = test_client.post("/sessions/cleanup", params={"days": 7})
    assert response.status_code == 200
    data = response.json()
    assert "deleted" in data


def test_error_handling_invalid_request(test_client):
    """Test error handling for invalid requests."""
    response = test_client.post(
        "/chat/stream",
        json={"query": "", "session_id": "test"}  # Empty query
    )
    # Should still return 200 but with error in stream
    assert response.status_code == 200


@pytest.mark.asyncio
async def test_concurrent_sessions():
    """Test handling multiple concurrent sessions."""
    from video_agent.session_manager import SessionManager

    manager = SessionManager(db_name="video_agent_test")
    await manager.connect()

    # Clean up
    await manager.sessions.delete_many({})

    # Create multiple sessions concurrently
    async def create_and_use_session(session_id):
        await manager.create_session(session_id)
        await manager.append_message(session_id, {"role": "user", "content": "test"})
        messages = await manager.get_messages(session_id)
        return len(messages)

    tasks = [create_and_use_session(f"session-{i}") for i in range(10)]
    results = await asyncio.gather(*tasks)

    assert all(r == 1 for r in results)
    await manager.close()


if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s"])
