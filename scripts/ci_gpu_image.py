"""Decide whether CI must rebuild the Worker prototype; unknown comparisons build safely."""

import json
import os
import subprocess
from pathlib import Path


def relevant(path):
    return path.startswith("transcode-worker/") or path in {
        ".github/workflows/ci.yml", "scripts/ci_gpu_image.py", "scripts/tests/test_ci_gpu_image.py",
    }


def required(event_name, base, head):
    if event_name == "workflow_dispatch" or not base or not head or set(base) == {"0"}:
        return True
    comparison = base + "..." + head if event_name == "pull_request" else base
    command = ["git", "diff", "--name-only", "--no-renames", "-z", comparison]
    if event_name != "pull_request":
        command.append(head)
    try:
        result = subprocess.run(command, check=True, capture_output=True)
    except (OSError, subprocess.CalledProcessError):
        return True
    return any(relevant(path.decode("utf-8", errors="surrogateescape"))
               for path in result.stdout.split(b"\0") if path)


def main():
    event_name = os.environ["GITHUB_EVENT_NAME"]
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
    if event_name == "pull_request":
        base = event["pull_request"]["base"]["sha"]
        head = event["pull_request"]["head"]["sha"]
    else:
        base, head = event.get("before"), os.environ.get("GITHUB_SHA")
    build = required(event_name, base, head)
    with Path(os.environ["GITHUB_OUTPUT"]).open("a") as output:
        output.write("required=" + str(build).lower() + "\n")
    print("GPU prototype image: " + ("build and verify" if build else "unchanged; build skipped"))


if __name__ == "__main__":
    main()
