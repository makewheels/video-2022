"""Error handling and retry logic for agent operations."""

from __future__ import annotations

import asyncio
import logging
from functools import wraps
from typing import Any, Callable, TypeVar

logger = logging.getLogger(__name__)

T = TypeVar("T")


class AgentError(Exception):
    """Base exception for agent errors."""
    pass


class ToolExecutionError(AgentError):
    """Error during tool execution."""
    pass


class ModelAPIError(AgentError):
    """Error calling model API."""
    pass


class SessionError(AgentError):
    """Error managing session."""
    pass


def retry_async(max_attempts: int = 3, delay: float = 1.0, backoff: float = 2.0, exceptions: tuple = (Exception,)):
    """Retry decorator for async functions with exponential backoff."""
    def decorator(func: Callable[..., Any]) -> Callable[..., Any]:
        @wraps(func)
        async def wrapper(*args: Any, **kwargs: Any) -> Any:
            current_delay = delay
            last_exception = None

            for attempt in range(max_attempts):
                try:
                    return await func(*args, **kwargs)
                except exceptions as e:
                    last_exception = e
                    if attempt < max_attempts - 1:
                        logger.warning(
                            f"{func.__name__} failed (attempt {attempt + 1}/{max_attempts}): {e}. "
                            f"Retrying in {current_delay}s..."
                        )
                        await asyncio.sleep(current_delay)
                        current_delay *= backoff
                    else:
                        logger.error(f"{func.__name__} failed after {max_attempts} attempts: {e}")

            raise last_exception

        return wrapper
    return decorator


def retry_sync(max_attempts: int = 3, delay: float = 1.0, backoff: float = 2.0, exceptions: tuple = (Exception,)):
    """Retry decorator for sync functions with exponential backoff."""
    def decorator(func: Callable[..., Any]) -> Callable[..., Any]:
        @wraps(func)
        def wrapper(*args: Any, **kwargs: Any) -> Any:
            import time
            current_delay = delay
            last_exception = None

            for attempt in range(max_attempts):
                try:
                    return func(*args, **kwargs)
                except exceptions as e:
                    last_exception = e
                    if attempt < max_attempts - 1:
                        logger.warning(
                            f"{func.__name__} failed (attempt {attempt + 1}/{max_attempts}): {e}. "
                            f"Retrying in {current_delay}s..."
                        )
                        time.sleep(current_delay)
                        current_delay *= backoff
                    else:
                        logger.error(f"{func.__name__} failed after {max_attempts} attempts: {e}")

            raise last_exception

        return wrapper
    return decorator


class ErrorHandler:
    """Centralized error handling for agent operations."""

    @staticmethod
    def handle_tool_error(tool_name: str, error: Exception) -> dict[str, Any]:
        """Handle tool execution errors gracefully."""
        logger.error(f"Tool {tool_name} failed: {error}", exc_info=True)
        return {
            "error": f"工具执行失败: {str(error)}",
            "tool": tool_name,
            "type": "tool_error",
        }

    @staticmethod
    def handle_model_error(error: Exception) -> dict[str, Any]:
        """Handle model API errors."""
        logger.error(f"Model API error: {error}", exc_info=True)
        error_msg = str(error).lower()

        # Check for common error patterns
        if "rate" in error_msg and "limit" in error_msg:
            return {
                "error": "API 请求频率超限，请稍后重试",
                "type": "rate_limit",
                "retryable": True,
            }
        elif "timeout" in error_msg:
            return {
                "error": "API 请求超时，请重试",
                "type": "timeout",
                "retryable": True,
            }
        elif "authentication" in error_msg or "api" in error_msg and "key" in error_msg:
            return {
                "error": "API 认证失败，请检查配置",
                "type": "auth_error",
                "retryable": False,
            }
        else:
            return {
                "error": f"模型调用失败: {str(error)}",
                "type": "model_error",
                "retryable": True,
            }

    @staticmethod
    def handle_session_error(error: Exception) -> dict[str, Any]:
        """Handle session management errors."""
        logger.error(f"Session error: {error}", exc_info=True)
        return {
            "error": f"会话管理失败: {str(error)}",
            "type": "session_error",
            "retryable": True,
        }

    @staticmethod
    def is_retryable(error: Exception) -> bool:
        """Check if an error is retryable."""
        error_msg = str(error).lower()
        retryable_patterns = ["timeout", "rate", "limit", "connection", "temporary", "503", "502", "504"]
        return any(pattern in error_msg for pattern in retryable_patterns)
