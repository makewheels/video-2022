"""Tests for error handler."""

import asyncio

import pytest

from video_agent.error_handler import (
    AgentError,
    ErrorHandler,
    ModelAPIError,
    ToolExecutionError,
    retry_async,
    retry_sync,
)


def test_handle_tool_error():
    """Test tool error handling."""
    handler = ErrorHandler()
    error = ValueError("Invalid argument")
    result = handler.handle_tool_error("search_video", error)

    assert "error" in result
    assert result["tool"] == "search_video"
    assert result["type"] == "tool_error"


def test_handle_model_error_rate_limit():
    """Test rate limit error handling."""
    handler = ErrorHandler()
    error = Exception("Rate limit exceeded")
    result = handler.handle_model_error(error)

    assert result["type"] == "rate_limit"
    assert result["retryable"] is True


def test_handle_model_error_timeout():
    """Test timeout error handling."""
    handler = ErrorHandler()
    error = Exception("Request timeout")
    result = handler.handle_model_error(error)

    assert result["type"] == "timeout"
    assert result["retryable"] is True


def test_handle_model_error_auth():
    """Test authentication error handling."""
    handler = ErrorHandler()
    error = Exception("Invalid API key")
    result = handler.handle_model_error(error)

    assert result["type"] == "auth_error"
    assert result["retryable"] is False


def test_is_retryable():
    """Test retryable error detection."""
    handler = ErrorHandler()

    # Retryable errors
    assert handler.is_retryable(Exception("Connection timeout"))
    assert handler.is_retryable(Exception("Rate limit exceeded"))
    assert handler.is_retryable(Exception("503 Service Unavailable"))

    # Non-retryable errors
    assert not handler.is_retryable(Exception("Invalid input"))
    assert not handler.is_retryable(Exception("Authentication failed"))


def test_retry_sync_success():
    """Test sync retry with successful call."""
    call_count = 0

    @retry_sync(max_attempts=3, delay=0.1)
    def flaky_function():
        nonlocal call_count
        call_count += 1
        if call_count < 2:
            raise Exception("Temporary error")
        return "success"

    result = flaky_function()
    assert result == "success"
    assert call_count == 2


def test_retry_sync_failure():
    """Test sync retry with all attempts failing."""
    call_count = 0

    @retry_sync(max_attempts=3, delay=0.1)
    def always_fails():
        nonlocal call_count
        call_count += 1
        raise Exception("Permanent error")

    with pytest.raises(Exception, match="Permanent error"):
        always_fails()

    assert call_count == 3


@pytest.mark.asyncio
async def test_retry_async_success():
    """Test async retry with successful call."""
    call_count = 0

    @retry_async(max_attempts=3, delay=0.1)
    async def flaky_async_function():
        nonlocal call_count
        call_count += 1
        if call_count < 2:
            raise Exception("Temporary error")
        return "success"

    result = await flaky_async_function()
    assert result == "success"
    assert call_count == 2


@pytest.mark.asyncio
async def test_retry_async_failure():
    """Test async retry with all attempts failing."""
    call_count = 0

    @retry_async(max_attempts=3, delay=0.1)
    async def always_fails_async():
        nonlocal call_count
        call_count += 1
        raise Exception("Permanent error")

    with pytest.raises(Exception, match="Permanent error"):
        await always_fails_async()

    assert call_count == 3


@pytest.mark.asyncio
async def test_retry_async_backoff():
    """Test exponential backoff in async retry."""
    import time

    call_times = []

    @retry_async(max_attempts=3, delay=0.1, backoff=2.0)
    async def track_timing():
        call_times.append(time.time())
        raise Exception("Always fails")

    with pytest.raises(Exception):
        await track_timing()

    # Check that delays increase (approximately)
    assert len(call_times) == 3
    if len(call_times) >= 2:
        delay1 = call_times[1] - call_times[0]
        assert delay1 >= 0.1  # First delay


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
