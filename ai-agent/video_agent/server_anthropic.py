"""FastAPI HTTP server with Anthropic Agent SDK and session management."""

from __future__ import annotations

import json
import os
import re
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, StreamingResponse
from pydantic import BaseModel

from .assistant import SYSTEM_PROMPT
from .client import ALL_TOOLS
from .tools import VideoTools
from .anthropic_client import AnthropicAgentClient


class ChatRequest(BaseModel):
    query: str
    session_id: str = "default"
    confirm_write: bool = False


def _strip_think(text: str) -> str:
    return re.sub(r"<think>.*?</think>\s*", "", text, flags=re.DOTALL).strip()


def convert_tools_to_anthropic_format(openai_tools: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Convert OpenAI tool format to Anthropic tool format."""
    anthropic_tools = []
    for tool in openai_tools:
        if tool["type"] == "function":
            func = tool["function"]
            anthropic_tools.append({
                "name": func["name"],
                "description": func["description"],
                "input_schema": func["parameters"],
            })
    return anthropic_tools


def create_app(tools: VideoTools) -> FastAPI:
    app = FastAPI(title="video-2022 AI Agent", version="0.3.0")

    app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

    # Initialize Anthropic client
    api_key = os.getenv("ANTHROPIC_API_KEY")
    model = os.getenv("ANTHROPIC_MODEL", "claude-3-5-sonnet-20241022")

    if not api_key:
        print("⚠️  ANTHROPIC_API_KEY not set, using fallback mode")
        agent_client = None
    else:
        agent_client = AnthropicAgentClient(api_key=api_key, model=model)

    # Convert tools to Anthropic format
    anthropic_tools = convert_tools_to_anthropic_format(ALL_TOOLS)

    @app.get("/health")
    async def health():
        return {
            "status": "ok",
            "backend": tools.backend,
            "model": model if agent_client else "fallback",
            "sdk": "anthropic",
            "tools_count": len(anthropic_tools),
        }

    @app.get("/tools")
    async def list_tools():
        return {"tools": anthropic_tools}

    @app.get("/")
    async def index():
        path = Path(__file__).resolve().parent / "web" / "index.html"
        return FileResponse(str(path))

    @app.post("/chat/stream")
    async def chat_stream(req: ChatRequest):
        """Full SSE streaming with Anthropic Agent SDK and session management."""
        if not agent_client:
            raise HTTPException(status_code=503, detail="Anthropic API not configured")

        if req.confirm_write:
            tools.confirm_write = True

        async def generate():
            max_turns = 8
            session_id = req.session_id

            try:
                # First turn: send user query
                response, messages = agent_client.chat(
                    query=req.query,
                    session_id=session_id,
                    system_prompt=SYSTEM_PROMPT,
                    tools=anthropic_tools,
                )

                for turn in range(max_turns):
                    # Extract text and tool calls
                    text = agent_client.get_text_from_response(response)
                    text = _strip_think(text)
                    tool_calls = agent_client.get_tool_calls_from_response(response)

                    # If no tool calls, we're done
                    if not tool_calls:
                        if text:
                            yield f"data: {json.dumps({'type': 'text', 'text': text, 'finish': True}, ensure_ascii=False)}\n\n"
                        break

                    # Send text if any
                    if text:
                        yield f"data: {json.dumps({'type': 'text', 'text': text, 'finish': False}, ensure_ascii=False)}\n\n"

                    # Execute tools
                    has_confirmation_needed = False
                    for tc in tool_calls:
                        # Send tool start event
                        yield f"data: {json.dumps({'type': 'tool_start', 'tool': {'name': tc['name'], 'args': tc['input']}}, ensure_ascii=False)}\n\n"

                        # Execute tool
                        result = tools.execute(tc["name"], tc["input"])

                        # Send tool result event
                        yield f"data: {json.dumps({'type': 'tool_call', 'tool': {'name': tc['name'], 'args': tc['input'], 'result': result}}, ensure_ascii=False, default=str)}\n\n"

                        # Add tool result to conversation
                        agent_client.add_tool_result(
                            session_id=session_id,
                            tool_use_id=tc["id"],
                            result=result,
                        )

                        # Check if confirmation needed
                        if isinstance(result, dict) and result.get("requiresConfirmation"):
                            has_confirmation_needed = True

                    # Stop if confirmation needed
                    if has_confirmation_needed:
                        yield f"data: {json.dumps({'type': 'confirmation_needed', 'message': '以上写操作需要确认'}, ensure_ascii=False)}\n\n"
                        break

                    # Continue conversation with tool results
                    response = agent_client.continue_conversation(
                        session_id=session_id,
                        system_prompt=SYSTEM_PROMPT,
                        tools=anthropic_tools,
                    )

                yield "data: [DONE]\n\n"

            except Exception as e:
                yield f"data: {json.dumps({'type': 'error', 'message': str(e)}, ensure_ascii=False)}\n\n"
            finally:
                tools.confirm_write = False

        return StreamingResponse(generate(), media_type="text/event-stream")

    @app.post("/session/clear")
    async def clear_session(session_id: str = "default"):
        """Clear a session's conversation history."""
        if agent_client:
            agent_client.clear_session(session_id)
        return {"status": "ok", "session_id": session_id}

    return app


def serve(tools: VideoTools, host: str = "127.0.0.1", port: int = 8765) -> None:
    import uvicorn

    app = create_app(tools)

    api_key = os.getenv("ANTHROPIC_API_KEY")
    model = os.getenv("ANTHROPIC_MODEL", "claude-3-5-sonnet-20241022")

    print(f"\n🎬 video-2022 AI Agent 启动中...")
    print(f"📍 聊天界面: http://{host}:{port}")
    print(f"📖 API 文档: http://{host}:{port}/docs")
    print(f"🤖 SDK:      Anthropic Agent SDK")
    print(f"🤖 模型:     {model if api_key else 'Not configured'}")
    print(f"📡 后端:     {tools.backend}")
    print(f"🔧 工具数:   {len(ALL_TOOLS)}")
    print(f"💬 多轮对话: ✅ 已启用")
    print()

    os.makedirs(Path(__file__).resolve().parent / "web", exist_ok=True)
    uvicorn.run(app, host=host, port=port, log_level="info")
