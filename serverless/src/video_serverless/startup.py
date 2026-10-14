"""Measure fresh local interpreters; deliberately not FC cold-start measurements."""

import argparse
import json
import os
import subprocess
import sys
from time import perf_counter

from video_serverless.benchmark import percentile

CHILD = """
import importlib, json, sys, time
started = time.perf_counter()
importlib.import_module(sys.argv[1])
print(json.dumps({'importMs': (time.perf_counter() - started) * 1000}))
"""


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--count", type=int, default=20)
    args = parser.parse_args()
    if args.count < 1:
        parser.error("count must be positive")
    results = {}
    for name, module in [("handler", "video_serverless.fc_handler"), ("fastapi", "video_serverless.web")]:
        samples = []
        for _ in range(args.count):
            started = perf_counter()
            process = subprocess.run(
                [sys.executable, "-c", CHILD, module], capture_output=True, text=True, timeout=30, check=True,
                creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
            )
            samples.append({**json.loads(process.stdout), "processMs": (perf_counter() - started) * 1000})
        times = [sample["importMs"] for sample in samples]
        results[name] = {
            "p50ImportMs": percentile(times, 50),
            "p95ImportMs": percentile(times, 95) if args.count >= 20 else None,
            "samples": samples,
        }
    print(json.dumps({
        "environment": "local", "python": sys.version.split()[0], "coldStartVerified": False,
        "note": "Includes local module imports; excludes FC scheduling, networking and Mongo authentication.",
        "results": results,
    }))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
