"""Tests for MongoDB session manager."""

import asyncio
from datetime import datetime, timedelta, timezone

import pytest
import pytest_asyncio

from video_agent.session_manager import SessionManager


@pytest_asyncio.fixture
async def session_manager():
    """Create a test session manager."""
    manager = SessionManager(mongo_uri="mongodb://localhost:27017", db_name="video_agent_test")
    await manager.connect()
    # Clean up test data
    await manager.sessions.delete_many({})
    yield manager
    await manager.close()


@pytest.mark.asyncio
async def test_create_and_get_session(session_manager):
    """Test creating and retrieving a session."""
    session_id = "test-session-1"
    metadata = {"user_id": "user123"}

    # Create session
    session = await session_manager.create_session(session_id, metadata)
    assert session["session_id"] == session_id
    assert session["metadata"] == metadata
    assert session["messages"] == []

    # Get session
    retrieved = await session_manager.get_session(session_id)
    assert retrieved["session_id"] == session_id
    assert retrieved["metadata"] == metadata


@pytest.mark.asyncio
async def test_append_message(session_manager):
    """Test appending messages to a session."""
    session_id = "test-session-2"

    # Append messages
    await session_manager.append_message(session_id, {"role": "user", "content": "Hello"})
    await session_manager.append_message(session_id, {"role": "assistant", "content": "Hi there"})

    # Get messages
    messages = await session_manager.get_messages(session_id)
    assert len(messages) == 2
    assert messages[0]["role"] == "user"
    assert messages[1]["role"] == "assistant"


@pytest.mark.asyncio
async def test_update_session(session_manager):
    """Test updating session messages."""
    session_id = "test-session-3"

    # Create initial session
    await session_manager.create_session(session_id)

    # Update with new messages
    new_messages = [
        {"role": "user", "content": "Question 1"},
        {"role": "assistant", "content": "Answer 1"},
    ]
    await session_manager.update_session(session_id, new_messages)

    # Verify
    messages = await session_manager.get_messages(session_id)
    assert len(messages) == 2
    assert messages[0]["content"] == "Question 1"


@pytest.mark.asyncio
async def test_get_messages_with_limit(session_manager):
    """Test getting messages with limit."""
    session_id = "test-session-4"

    # Add multiple messages
    for i in range(10):
        await session_manager.append_message(session_id, {"role": "user", "content": f"Message {i}"})

    # Get last 3 messages
    messages = await session_manager.get_messages(session_id, limit=3)
    assert len(messages) == 3
    assert messages[-1]["content"] == "Message 9"


@pytest.mark.asyncio
async def test_delete_session(session_manager):
    """Test deleting a session."""
    session_id = "test-session-5"

    # Create and delete
    await session_manager.create_session(session_id)
    await session_manager.delete_session(session_id)

    # Verify deleted
    session = await session_manager.get_session(session_id)
    assert session is None


@pytest.mark.asyncio
async def test_cleanup_old_sessions(session_manager):
    """Test cleaning up old sessions."""
    # Create old session
    old_session_id = "old-session"
    await session_manager.create_session(old_session_id)

    # Manually set old timestamp
    old_time = datetime.now(timezone.utc) - timedelta(days=10)
    await session_manager.sessions.update_one(
        {"session_id": old_session_id},
        {"$set": {"updated_at": old_time}}
    )

    # Create recent session
    recent_session_id = "recent-session"
    await session_manager.create_session(recent_session_id)

    # Cleanup sessions older than 7 days
    deleted_count = await session_manager.cleanup_old_sessions(days=7)
    assert deleted_count == 1

    # Verify
    old = await session_manager.get_session(old_session_id)
    recent = await session_manager.get_session(recent_session_id)
    assert old is None
    assert recent is not None


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
