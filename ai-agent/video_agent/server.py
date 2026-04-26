"""FastAPI HTTP server with full SSE streaming agent loop + web chat UI."""

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
from .client import ALL_TOOLS, AgentResponse, ModelClient, ToolCall
from .tools import VideoTools


class ChatRequest(BaseModel):
    query: str
    session_id: str = "default"
    confirm_write: bool = False


def _strip_think(text: str) -> str:
    return re.sub(r"<think>.*?</think>\s*", "", text, flags=re.DOTALL).strip()


def create_app(assistant, tools) -> FastAPI:
    app = FastAPI(title="video-2022 AI Agent", version="0.2.0")

    app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

    @app.get("/health")
    async def health():
        return {
            "status": "ok",
            "backend": tools.backend,
            "model": assistant.client.model,
            "base_url": assistant.client.base_url,
            "tools_count": len(ALL_TOOLS),
        }

    @app.get("/tools")
    async def list_tools():
        return {"tools": ALL_TOOLS}

    @app.get("/")
    async def index():
        path = Path(__file__).resolve().parent / "web" / "index.html"
        return FileResponse(str(path))

    @app.post("/chat")
    async def chat(req: ChatRequest):
        try:
            if req.confirm_write:
                tools.confirm_write = True
            result = assistant.answer(req.query)
            tools.confirm_write = False
            return {"answer": result["answer"], "trace": result.get("trace", [])}
        except Exception as e:
            raise HTTPException(status_code=500, detail=str(e))

    @app.post("/chat/stream")
    async def chat_stream(req: ChatRequest):
        """Full SSE streaming with agent loop."""
        if req.confirm_write:
            tools.confirm_write = True

        client = assistant.client
        messages: list[dict[str, Any]] = [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": req.query},
        ]

        async def generate():
            max_turns = 8
            for _ in range(max_turns):
                # Build SSE event
                event = client.chat(messages, stream=False)
                # Strip thinking
                text = _strip_think(event.text)

                if not event.has_tool_calls:
                    yield f"data: {json.dumps({'type': 'text', 'text': text, 'finish': True}, ensure_ascii=False)}\n\n"
                    break

                if text:
                    yield f"data: {json.dumps({'type': 'text', 'text': text, 'finish': False}, ensure_ascii=False)}\n\n"

                # Execute tools
                tool_results = []
                for tc in event.tool_calls:
                    yield f"data: {json.dumps({'type': 'tool_start', 'tool': {'name': tc.name, 'args': tc.arguments}}, ensure_ascii=False)}\n\n"
                    result = tools.execute(tc.name, tc.arguments)
                    tool_results.append({"name": tc.name, "args": tc.arguments, "result": result})
                    # Send full result to frontend, not summarized
                    yield f"data: {json.dumps({'type': 'tool_call', 'tool': {'name': tc.name, 'args': tc.arguments, 'result': result}}, ensure_ascii=False, default=str)}\n\n"

                # Append assistant + tool messages
                assistant_msg: dict[str, Any] = {"role": "assistant", "content": text or None}
                if event.tool_calls:
                    assistant_msg["tool_calls"] = [
                        {"id": tc.id, "type": "function", "function": {"name": tc.name, "arguments": json.dumps(tc.arguments, ensure_ascii=False)}}
                        for tc in event.tool_calls
                    ]
                messages.append(assistant_msg)
                for tc, tr in zip(event.tool_calls, tool_results):
                    messages.append({
                        "role": "tool",
                        "tool_call_id": tc.id,
                        "content": json.dumps(tr["result"], ensure_ascii=False, default=str),
                    })

                # Stop if any tool requires confirmation
                if any(isinstance(tr["result"], dict) and tr["result"].get("requiresConfirmation") for tr in tool_results):
                    yield f"data: {json.dumps({'type': 'confirmation_needed', 'message': '以上写操作需要确认'}, ensure_ascii=False)}\n\n"
                    break

            yield "data: [DONE]\n\n"

        return StreamingResponse(generate(), media_type="text/event-stream")

    return app


def _summarize(result: Any) -> str:
    if not isinstance(result, dict):
        return str(result)[:200]
    if "error" in result:
        return f"Error: {result['error']}"[:200]
    if "requiresConfirmation" in result:
        return f"⚠️ 需要确认: {result.get('message', '')}"[:200]
    # Brief summary
    for key in ("total", "count", "status", "videoId", "title", "shortCode", "id", "nickname"):
        if key in result:
            return json.dumps(result, ensure_ascii=False)[:300]
    return json.dumps(result, ensure_ascii=False)[:300]


def serve(assistant, tools, host: str = "127.0.0.1", port: int = 8765) -> None:
    import uvicorn

    app = create_app(assistant, tools)
    print(f"\n🎬 video-2022 AI Agent 启动中...")
    print(f"📍 聊天界面: http://{host}:{port}")
    print(f"📖 API 文档: http://{host}:{port}/docs")
    print(f"🤖 模型:     {assistant.client.model}")
    print(f"📡 后端:     {tools.backend}")
    print(f"🔧 工具数:   {len(ALL_TOOLS)}")
    print()
    os.makedirs(Path(__file__).resolve().parent / "web", exist_ok=True)
    uvicorn.run(app, host=host, port=port, log_level="info")
