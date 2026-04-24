"""Command entry point for the video agent prototype."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from .assistant import VideoAssistant
from .eval_runner import run_eval_suite
from .tools import VideoTools


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="python -m video_agent")
    sub = parser.add_subparsers(dest="command", required=True)

    ask = sub.add_parser("ask", help="Ask the video assistant a natural-language question")
    ask.add_argument("query")
    ask.add_argument("--backend", choices=["fixture", "cli"], default="fixture")
    ask.add_argument("--planner", choices=["heuristic", "llm", "minimax"], default="heuristic")
    ask.add_argument("--fixture", default=str(Path(__file__).resolve().parents[1] / "fixtures" / "videos.json"))
    ask.add_argument("--base-url", default=None)
    ask.add_argument("--token", default=None)
    ask.add_argument("--confirm-write", action="store_true")
    ask.add_argument("--json", action="store_true", help="Print full JSON result")

    ev = sub.add_parser("eval", help="Run the local evaluation suite")
    ev.add_argument("--backend", choices=["fixture", "cli"], default="fixture")
    ev.add_argument("--planner", choices=["heuristic", "llm", "minimax"], default="heuristic")
    ev.add_argument("--fixture", default=str(Path(__file__).resolve().parents[1] / "fixtures" / "videos.json"))
    ev.add_argument("--cases", default=str(Path(__file__).resolve().parents[1] / "evals" / "video_agent_eval.jsonl"))
    ev.add_argument("--base-url", default=None)
    ev.add_argument("--token", default=None)
    ev.add_argument("--json", action="store_true")

    return parser


def main() -> None:
    args = build_parser().parse_args()
    tools = VideoTools(
        backend=args.backend,
        fixture_path=getattr(args, "fixture", None),
        base_url=getattr(args, "base_url", None),
        token=getattr(args, "token", None),
        confirm_write=getattr(args, "confirm_write", False),
    )

    if args.command == "ask":
        assistant = VideoAssistant(tools=tools, planner=args.planner)
        result = assistant.answer(args.query)
        if args.json:
            print(json.dumps(result, ensure_ascii=False, indent=2, default=str))
        else:
            print(result["answer"])
        return

    if args.command == "eval":
        assistant = VideoAssistant(tools=tools, planner=args.planner)
        report = run_eval_suite(assistant, args.cases)
        if args.json:
            print(json.dumps(report, ensure_ascii=False, indent=2, default=str))
        else:
            print(f"passed={report['passed']} failed={report['failed']} total={report['total']}")
            for item in report["results"]:
                status = "PASS" if item["passed"] else "FAIL"
                print(f"{status} {item['id']}: {item['query']}")
                if not item["passed"]:
                    print(f"  reasons: {', '.join(item['reasons'])}")
        return


if __name__ == "__main__":
    main()
