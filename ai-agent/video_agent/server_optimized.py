"""Optimized FastAPI server with MongoDB session management and error handling."""

from __future__ import annotations

import json
import logging
import os
import re
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, StreamingResponse
from pydantic import BaseModel

from .assistant import SYSTEM_PROMPT
from .client import ALL_TOOLS, ModelClient
from .context_manager import ContextManager
from .error_handler import ErrorHandler, ModelAPIError, retry_async
from .session_manager import SessionManager
from .tools import VideoTools

logger = logging.getLogger(__name__)


class ChatRequest(BaseModel):
    query: str
    session_id: str = "default"
    confirm_write: bool = False


def _strip_think(text: str) -> str:
    return re.sub(r"<think>.*?</think>\s*", "", text, flags=re.DOTALL).strip()


def create_optimized_app(client: ModelClient, tools: VideoTools) -> FastAPI:
    app = FastAPI(title="video-2022 AI Agent (Optimized)", version="0.3.0")
    app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

    # Initialize managers
    session_manager = SessionManager()
    context_manager = ContextManager(max_tokens=8000)
    error_handler = ErrorHandler()

    @app.on_event("startup")
    async def startup():
        await session_manager.connect()
        logger.info("MongoDB session manager connected")

    @app.on_event("shutdown")
    async def shutdown():
        await session_manager.close()
        logger.info("MongoDB session manager closed")

    @app.get("/health")
    async def health():
        return {
            "status": "ok",
            "backend": tools.backend,
            "model": client.model,
            "base_url": client.base_url,
            "tools_count": len(ALL_TOOLS),
            "features": ["mongodb_sessions", "context_management", "error_handling"],
        }

    @app.get("/tools")
    async def list_tools():
        return {"tools": ALL_TOOLS}

    @app.get("/")
    async def index():
        path = Path(__file__).resolve().parent / "web" / "index.html"
        return FileResponse(str(path))

    @app.post("/chat/stream")
    async def chat_stream(req: ChatRequest):
        """Optimized SSE streaming with session management and error handling."""
        if req.confirm_write:
            tools.confirm_write = True

        async def generate():
            try:
                # Load session history
                history = await session_manager.get_messages(req.session_id)

                # Build messages with context management
                messages: list[dict[str, Any]] = [
                    {"role": "system", "content": SYSTEM_PROMPT},
                ]
                messages.extend(history)
                messages.append({"role": "user", "content": req.query})

                # Trim if needed
                messages = context_manager.trim_messages(messages, keep_recent=6)

                # Save user message
                await session_manager.append_message(req.session_id, {"role": "user", "content": req.query})

                max_turns = 8
                for turn in range(max_turns):
                    try:
                        # Call model with retry
                        event = await _call_model_with_retry(client, messages)
                        text = _strip_think(event.text)

                        # No tool calls - final response
                        if not event.has_tool_calls:
                            if text:
                                yield f"data: {json.dumps({'type': 'text', 'text': text, 'finish': True}, ensure_ascii=False)}\n\n"
                                await session_manager.append_message(req.session_id, {"role": "assistant", "content": text})
                            break

                        # Stream text if present
                        if text:
                            yield f"data: {json.dumps({'type': 'text', 'text': text, 'finish': False}, ensure_ascii=False)}\n\n"

                        # Execute tools with error handling
                        tool_results = []
                        for tc in event.tool_calls:
                            yield f"data: {json.dumps({'type': 'tool_start', 'tool': {'name': tc.name, 'args': tc.arguments}}, ensure_ascii=False)}\n\n"

                            try:
                                result = tools.execute(tc.name, tc.arguments)
                                tool_results.append({"name": tc.name, "args": tc.arguments, "result": result})
                                yield f"data: {json.dumps({'type': 'tool_call', 'tool': {'name': tc.name, 'args': tc.arguments, 'result': result}}, ensure_ascii=False, default=str)}\n\n"
                            except Exception as e:
                                error_result = error_handler.handle_tool_error(tc.name, e)
                                tool_results.append({"name": tc.name, "args": tc.arguments, "result": error_result})
                                yield f"data: {json.dumps({'type': 'tool_call', 'tool': {'name': tc.name, 'args': tc.arguments, 'result': error_result}}, ensure_ascii=False)}\n\n"

                        # Build assistant message
                        assistant_msg: dict[str, Any] = {"role": "assistant", "content": text or None}
                        if event.tool_calls:
                            assistant_msg["tool_calls"] = [
                                {"id": tc.id, "type": "function", "function": {"name": tc.name, "arguments": json.dumps(tc.arguments, ensure_ascii=False)}}
                                for tc in event.tool_calls
                            ]
                        messages.append(assistant_msg)

                        # Save assistant message
                        await session_manager.append_message(req.session_id, assistant_msg)

                        # Add tool results to messages
                        for tc, tr in zip(event.tool_calls, tool_results):
                            tool_msg = {
                                "role": "tool",
                                "tool_call_id": tc.id,
                                "content": json.dumps(tr["result"], ensure_ascii=False, default=str),
                            }
                            messages.append(tool_msg)
                            await session_manager.append_message(req.session_id, tool_msg)

                        # Check for confirmation needed
                        if any(isinstance(tr["result"], dict) and tr["result"].get("requiresConfirmation") for tr in tool_results):
                            yield f"data: {json.dumps({'type': 'confirmation_needed', 'message': '以上写操作需要确认'}, ensure_ascii=False)}\n\n"
                            break

                    except ModelAPIError as e:
                        error_info = error_handler.handle_model_error(e)
                        yield f"data: {json.dumps({'type': 'error', 'error': error_info}, ensure_ascii=False)}\n\n"
                        break
                    except Exception as e:
                        logger.error(f"Unexpected error in turn {turn}: {e}", exc_info=True)
                        yield f"data: {json.dumps({'type': 'error', 'error': {'message': str(e), 'type': 'unknown'}}, ensure_ascii=False)}\n\n"
                        break

                yield "data: [DONE]\n\n"

            except Exception as e:
                logger.error(f"Fatal error in chat_stream: {e}", exc_info=True)
                yield f"data: {json.dumps({'type': 'error', 'error': {'message': '服务器错误', 'details': str(e)}}, ensure_ascii=False)}\n\n"
                yield "data: [DONE]\n\n"
            finally:
                tools.confirm_write = False

        return StreamingResponse(generate(), media_type="text/event-stream")

    @app.delete("/sessions/{session_id}")
    async def delete_session(session_id: str):
        """Delete a session."""
        try:
            await session_manager.delete_session(session_id)
            return {"status": "ok", "message": f"Session {session_id} deleted"}
        except Exception as e:
            raise HTTPException(status_code=500, detail=str(e))

    @app.post("/sessions/cleanup")
    async def cleanup_sessions(days: int = 7):
        """Cleanup old sessions."""
        try:
            count = await session_manager.cleanup_old_sessions(days)
            return {"status": "ok", "deleted": count}
        except Exception as e:
            raise HTTPException(status_code=500, detail=str(e))

    return app


@retry_async(max_attempts=3, delay=1.0, exceptions=(Exception,))
async def _call_model_with_retry(client: ModelClient, messages: list[dict[str, Any]]):
    """Call model API with retry logic."""
    try:
        # Convert sync call to async (client.chat is sync)
        import asyncio
        loop = asyncio.get_event_loop()
        return await loop.run_in_executor(None, lambda: client.chat(messages, stream=False))
    except Exception as e:
        logger.error(f"Model API call failed: {e}")
        raise ModelAPIError(f"Model API error: {e}") from e


def serve_optimized(client: ModelClient, tools: VideoTools, host: str = "127.0.0.1", port: int = 8765) -> None:
    import uvicorn

    app = create_optimized_app(client, tools)
    print(f"\n🎬 video-2022 AI Agent (Optimized) 启动中...")
    print(f"📍 聊天界面: http://{host}:{port}")
    print(f"📖 API 文档: http://{host}:{port}/docs")
    print(f"🤖 模型:     {client.model}")
    print(f"📡 后端:     {tools.backend}")
    print(f"🔧 工具数:   {len(ALL_TOOLS)}")
    print(f"💾 会话存储: MongoDB")
    print(f"🛡️  错误处理: 启用")
    print()
    os.makedirs(Path(__file__).resolve().parent / "web", exist_ok=True)
    uvicorn.run(app, host=host, port=port, log_level="info")
