"""Linux candidate smoke test; a local runtime check, not an FC deployment."""

import argparse
import importlib
import json
import os
import socket
import subprocess
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

# -I ignores the script directory; add only the committed tool directory.
sys.path.insert(0, str(Path(__file__).resolve().parent))
from release_support import read_zip  # noqa: E402


def smoke(directory: Path, flavor: str):
    root = Path(tempfile.gettempdir()).resolve()
    with tempfile.TemporaryDirectory(prefix="video-release-smoke-", dir=root) as name:
        task = Path(name).resolve()
        assert task.parent == root and task.name.startswith("video-release-smoke-")
        for filename, data in read_zip(directory / "payload.zip").items():
            path = task / filename
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        sys.path.insert(0, str(task))
        assert not any(name.startswith("video_serverless") for name in sys.modules)
        os.environ["PROTOTYPE_TOKEN"] = "isolated-build-probe"
        for key in ["MONGODB_URI", "MONGODB_DATABASE"]:
            os.environ.pop(key, None)
        if flavor == "handler":
            handler = importlib.import_module("video_serverless.fc_handler").handler
            assert handler({"rawPath": "/healthCheck"}, None)["statusCode"] == 200
            assert "pymongo" not in sys.modules and "fastapi" not in sys.modules
            reply = handler({"rawPath": "/_prototype/empty",
                             "headers": {"X-Prototype-Token": "isolated-build-probe"}}, None)
            assert json.loads(reply["body"]) == {"ok": True}
        else:
            web_smoke(task)
        # Import native modules so pure-Python fallbacks cannot hide bad wheels.
        importlib.import_module("bson._cbson")
        if flavor == "web":
            importlib.import_module("pydantic_core._pydantic_core")
    print("Linux ZIP smoke passed: " + flavor)


def web_smoke(task: Path):
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        listener.listen()
        port = listener.getsockname()[1]
        env = {**os.environ, "PYTHONPATH": str(task), "PYTHONDONTWRITEBYTECODE": "1"}
        child = subprocess.Popen(
            [sys.executable, "-S", "-m", "uvicorn", "video_serverless.web:app", "--fd", str(listener.fileno()),
             "--log-level", "error"], cwd=task, env=env, pass_fds=(listener.fileno(),),
        )
        try:
            deadline = time.monotonic() + 10
            while True:
                try:
                    with urllib.request.urlopen(f"http://127.0.0.1:{port}/healthCheck", timeout=0.5) as response:
                        assert response.read().startswith(b"ok ")
                    break
                except OSError:
                    if child.poll() is not None or time.monotonic() >= deadline:
                        raise RuntimeError("ASGI candidate did not start") from None
                    time.sleep(0.05)
            request = urllib.request.Request(f"http://127.0.0.1:{port}/_prototype/empty",
                                             headers={"X-Prototype-Token": "isolated-build-probe"})
            with urllib.request.urlopen(request, timeout=2) as response:
                assert json.load(response) == {"ok": True}
        finally:
            child.terminate()
            try:
                child.wait(timeout=5)
            except subprocess.TimeoutExpired:
                child.kill()
                child.wait(timeout=5)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--flavor", choices=["handler", "web"], required=True)
    args = parser.parse_args()
    smoke(args.directory, args.flavor)
