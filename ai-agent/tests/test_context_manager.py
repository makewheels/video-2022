"""Tests for context manager."""

import pytest

from video_agent.context_manager import ContextManager


@pytest.fixture
def context_manager():
    """Create a test context manager."""
    return ContextManager(max_tokens=1000, system_prompt_tokens=100)


def test_estimate_tokens(context_manager):
    """Test token estimation."""
    # English text
    english = "Hello world"
    tokens = context_manager.estimate_tokens(english)
    assert tokens > 0

    # Chinese text
    chinese = "你好世界"
    tokens = context_manager.estimate_tokens(chinese)
    assert tokens > 0

    # Mixed text
    mixed = "Hello 你好"
    tokens = context_manager.estimate_tokens(mixed)
    assert tokens > 0


def test_estimate_message_tokens(context_manager):
    """Test message token estimation."""
    message = {
        "role": "user",
        "content": "What is the weather today?"
    }
    tokens = context_manager.estimate_message_tokens(message)
    assert tokens > 4  # Base overhead + content


def test_trim_messages_no_trim_needed(context_manager):
    """Test trimming when no trim is needed."""
    messages = [
        {"role": "system", "content": "You are a helpful assistant."},
        {"role": "user", "content": "Hello"},
        {"role": "assistant", "content": "Hi there"},
    ]
    trimmed = context_manager.trim_messages(messages, keep_recent=2)
    assert len(trimmed) == 3  # All messages kept


def test_trim_messages_with_summary(context_manager):
    """Test trimming with summary of old messages."""
    messages = [
        {"role": "system", "content": "You are a helpful assistant."},
    ]
    # Add many messages to exceed token limit
    for i in range(20):
        messages.append({"role": "user", "content": f"Question {i}" * 10})
        messages.append({"role": "assistant", "content": f"Answer {i}" * 10})

    trimmed = context_manager.trim_messages(messages, keep_recent=4)

    # Should have: system + summary + recent messages
    assert len(trimmed) >= 5  # system + summary + 4 recent
    assert trimmed[0]["role"] == "system"
    assert "[对话摘要]" in trimmed[1]["content"]


def test_trim_messages_keeps_system(context_manager):
    """Test that system message is always kept."""
    messages = [
        {"role": "system", "content": "System prompt"},
        {"role": "user", "content": "x" * 1000},
    ]
    trimmed = context_manager.trim_messages(messages, keep_recent=1)
    assert trimmed[0]["role"] == "system"


def test_summarize_messages(context_manager):
    """Test message summarization."""
    messages = [
        {"role": "user", "content": "What is Python?"},
        {"role": "assistant", "content": "Python is a programming language."},
        {"role": "user", "content": "How do I install it?"},
    ]
    summary = context_manager._summarize_messages(messages)
    assert "用户" in summary
    assert "助手" in summary


def test_should_summarize(context_manager):
    """Test summarization threshold check."""
    # Small conversation
    small_messages = [
        {"role": "user", "content": "Hi"},
        {"role": "assistant", "content": "Hello"},
    ]
    assert not context_manager.should_summarize(small_messages)

    # Large conversation
    large_messages = [{"role": "user", "content": "x" * 500} for _ in range(10)]
    assert context_manager.should_summarize(large_messages)


def test_truncate(context_manager):
    """Test text truncation."""
    text = "a" * 100
    truncated = context_manager._truncate(text, 50)
    assert len(truncated) <= 53  # 50 + "..."
    assert truncated.endswith("...")


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
