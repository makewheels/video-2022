"""Command entry point — ask, interactive chat, eval."""

from __future__ import annotations

import argparse
import json
import os
import readline  # noqa: F401 — enables line-editing in input()
import sys
from pathlib import Path

from .assistant import VideoAssistant
from .config import get_config
from .eval_runner import run_eval_suite
from .tools import VideoTools


INTERACTIVE_BANNER = """
╔══════════════════════════════════════════╗
║     🎬 video-2022 AI 视频助手           ║
║                                         ║
║  试试这些问题：                          ║
║  · 我上传了几个视频？                   ║
║  · AI 教程播放量是多少？                 ║
║  · 我最近看过哪些视频？                  ║
║  · 搜索美食类公开视频                    ║
║  · 我有几条未读通知？                    ║
║  · 退出: /quit 或 Ctrl+C                ║
╚══════════════════════════════════════════╝
"""


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="python -m video_agent",
        description="video-2022 AI 视频助手 — 用自然语言管理视频平台",
    )

    sub = parser.add_subparsers(dest="command", required=True)

    # ask
    ask = sub.add_parser("ask", help="单次提问")
    ask.add_argument("query", nargs="+", help="自然语言问题")
    ask.add_argument("--backend", choices=["fixture", "cli"], default=None, help="工具后端")
    ask.add_argument("--model", default=None, help="模型名")
    ask.add_argument("--base-url", default=None, help="LLM API 地址")
    ask.add_argument("--api-key", default=None, help="LLM API key")
    ask.add_argument("--fixture", default=None)
    ask.add_argument("--token", default=None, help="video-cli token")
    ask.add_argument("--confirm-write", action="store_true", help="允许写操作")
    ask.add_argument("--json", action="store_true", help="JSON 格式输出")
    ask.add_argument("-s", "--stream", action="store_true", help="流式输出", default=True)

    # interactive / chat
    chat = sub.add_parser("chat", help="交互式对话")
    chat.add_argument("--backend", choices=["fixture", "cli"], default=None)
    chat.add_argument("--model", default=None)
    chat.add_argument("--base-url", default=None)
    chat.add_argument("--api-key", default=None)
    chat.add_argument("--fixture", default=None)
    chat.add_argument("--token", default=None)
    chat.add_argument("--confirm-write", action="store_true")

    # eval
    ev = sub.add_parser("eval", help="运行评估套件")
    ev.add_argument("--backend", choices=["fixture", "cli"], default=None)
    ev.add_argument("--model", default=None)
    ev.add_argument("--base-url", default=None)
    ev.add_argument("--api-key", default=None)
    ev.add_argument("--fixture", default=None)
    ev.add_argument("--token", default=None)
    ev.add_argument("--cases", default=None)
    ev.add_argument("--json", action="store_true")

    # serve
    serve = sub.add_parser("serve", help="启动 HTTP API 服务")
    serve.add_argument("--host", default="127.0.0.1", help="监听地址")
    serve.add_argument("--port", type=int, default=8765, help="监听端口")
    serve.add_argument("--backend", choices=["fixture", "cli"], default=None)
    serve.add_argument("--model", default=None)
    serve.add_argument("--base-url", default=None)
    serve.add_argument("--api-key", default=None)
    serve.add_argument("--fixture", default=None)
    serve.add_argument("--token", default=None)
    serve.add_argument("--optimized", action="store_true", help="使用优化版本（MongoDB + 错误处理）")

    return parser


def _make_assistant(args: argparse.Namespace) -> tuple[VideoAssistant, VideoTools]:
    cfg = get_config()

    backend = args.backend or cfg.backend
    tools = VideoTools(
        backend=backend,
        fixture_path=getattr(args, "fixture", None) or cfg.fixture_path,
        base_url=getattr(args, "base_url", None) or cfg.base_url_video,
        token=getattr(args, "token", None) or cfg.video_token,
        confirm_write=getattr(args, "confirm_write", False) or cfg.confirm_write,
    )
    assistant = VideoAssistant(
        tools=tools,
        model=getattr(args, "model", None) or cfg.model,
        base_url=getattr(args, "base_url", None) or cfg.base_url,
        api_key=getattr(args, "api_key", None) or cfg.api_key,
    )
    return assistant, tools


def main() -> None:
    args = build_parser().parse_args()

    if args.command == "ask":
        assistant, tools = _make_assistant(args)
        query = " ".join(args.query)
        if getattr(args, "stream", True) and not args.json:
            print(f"\n🤔 {query}\n")
            assistant.chat_stream(query)
        else:
            result = assistant.answer(query)
            if args.json:
                print(json.dumps(result, ensure_ascii=False, indent=2, default=str))
            else:
                print(f"\n🤔 {query}\n")
                print(result["answer"])
                if result.get("trace"):
                    print(f"\n📋 工具调用 ({len(result['trace'])} 次):")
                    for t in result["trace"]:
                        print(f"  · {t['name']}({json.dumps(t['args'], ensure_ascii=False)})")
        return

    if args.command == "chat":
        assistant, tools = _make_assistant(args)
        print(INTERACTIVE_BANNER)
        history: list[dict[str, Any]] = []
        print(f"📡 后端: {tools.backend}  |  🤖 模型: {assistant.client.model}  |  {assistant.client.base_url}")
        print()

        while True:
            try:
                query = input("💬 你: ").strip()
            except (EOFError, KeyboardInterrupt):
                print("\n👋 再见！")
                break

            if not query:
                continue
            if query in ("/quit", "/exit", "/q"):
                print("👋 再见！")
                break
            if query in ("/clear", "/c"):
                history = []
                print("✅ 对话历史已清除")
                continue
            if query in ("/confirm", "/y"):
                tools.confirm_write = True
                print("✅ 写操作确认已开启（本次会话）")
                continue

            print()
            try:
                assistant.chat_stream(query, history=history)
            except Exception as e:
                print(f"\n❌ 出错: {e}")

            # Save context for next turn (simple: last assistant + tool messages)
            # For simplicity, history accumulates. /clear resets.
            print()
        return

    if args.command == "eval":
        assistant, tools = _make_assistant(args)
        config = get_config()
        cases_path = args.cases or os.path.join(config.project_root, "evals", "video_agent_eval.jsonl")
        report = run_eval_suite(assistant, cases_path)

        if args.json:
            print(json.dumps(report, ensure_ascii=False, indent=2, default=str))
        else:
            print(f"\n📊 评估结果: {report['passed']}/{report['total']} 通过")
            if report["failed"] > 0:
                print(f"❌ {report['failed']} 条失败:\n")
            else:
                print("✅ 全部通过！\n")
            for item in report["results"]:
                icon = "✅" if item["passed"] else "❌"
                print(f"  {icon} {item['id']}: {item['query']}")
                if not item["passed"]:
                    print(f"    原因: {', '.join(item['reasons'])}")
                    ans = item.get("result", {}).get("answer", "")
                    if ans:
                        print(f"    回答: {ans[:120]}")
        return

    if args.command == "serve":
        assistant, tools = _make_assistant(args)

        # Check if using optimized version
        use_optimized = getattr(args, "optimized", False) or os.getenv("USE_OPTIMIZED") == "true"

        if use_optimized:
            from .server_optimized import serve_optimized
            print("🚀 使用优化版本（MongoDB + 错误处理 + 上下文管理）")
            serve_optimized(assistant.client, tools, host=args.host, port=args.port)
        else:
            from .server import serve
            serve(assistant, tools, host=args.host, port=args.port)
        return


if __name__ == "__main__":
    main()
