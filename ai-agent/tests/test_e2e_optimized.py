"""E2E tests for optimized agent server."""

import asyncio
import json
import time

import pytest
from fastapi.testclient import TestClient

from video_agent.client import AgentResponse, ToolCall
from video_agent.server_optimized import create_optimized_app
from video_agent.tools import VideoTools


def _mongo_available() -> bool:
    try:
        from pymongo import MongoClient

        MongoClient("mongodb://localhost:27017", serverSelectionTimeoutMS=500).admin.command("ping")
        return True
    except Exception:
        return False


class _FakeModelClient:
    """离线假模型客户端：第一轮固定调 search_public_videos，拿到工具结果后回答。"""

    model = "fake-model"
    base_url = "http://fake"

    def chat(self, messages, tools=None, *, stream=False):
        if messages and messages[-1].get("role") == "tool":
            return AgentResponse(text="这是基于工具结果的回答")
        return AgentResponse(
            tool_calls=[ToolCall(id="call-1", name="search_public_videos", arguments={"keyword": "测试"})]
        )


class _WriteAttemptModelClient(_FakeModelClient):
    """第一轮固定尝试 delete_video（未确认会被拒），用于验证确认反馈闭环。"""

    def chat(self, messages, tools=None, *, stream=False):
        if messages and messages[-1].get("role") == "tool":
            return AgentResponse(text="删除《测试》需要先确认，确认后我再执行")
        return AgentResponse(
            tool_calls=[ToolCall(id="call-1", name="delete_video", arguments={"video_id": "v1"})]
        )


@pytest.fixture
def test_client():
    """Create a test client with fixture backend (offline, requires local MongoDB)."""
    if not _mongo_available():
        pytest.skip("本机没有运行 MongoDB")
    app = create_optimized_app(_FakeModelClient(), VideoTools(backend="fixture"))
    # 用 with 包住：让 startup/shutdown 正常执行，且所有请求共享同一个事件循环
    # （否则 motor client 会绑定到已关闭的 loop 上，报 "Event loop is closed"）
    with TestClient(app) as client:
        yield client


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
        json={"query": "搜索关键词'测试'的视频", "session_id": f"basic-{int(time.time() * 1000)}"}
    )
    assert response.status_code == 200
    assert response.headers["content-type"] == "text/event-stream; charset=utf-8"

    # Parse SSE events
    events = []
    for line in response.iter_lines():
        if line.startswith("data: "):
            data = line[6:]
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


def test_chat_stream_confirmation_gets_explained():
    """写操作被拒（未确认）后，模型应多走一轮生成解释，confirmation_needed 之后还有 text 事件。"""
    if not _mongo_available():
        pytest.skip("本机没有运行 MongoDB")
    app = create_optimized_app(_WriteAttemptModelClient(), VideoTools(backend="fixture"))
    with TestClient(app) as client:
        response = client.post(
            "/chat/stream",
            json={"query": "删除《测试》", "session_id": f"confirm-{int(time.time())}"},
        )
    assert response.status_code == 200

    events = []
    for line in response.iter_lines():
        if line.startswith("data: "):
            data = line[6:]
            if data != "[DONE]":
                events.append(json.loads(data))

    types = [e["type"] for e in events]
    assert "confirmation_needed" in types
    confirm_idx = types.index("confirmation_needed")
    assert "text" in types[confirm_idx + 1:], "confirmation_needed 之后应有模型生成的解释文本"


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
    if not _mongo_available():
        pytest.skip("本机没有运行 MongoDB")
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
