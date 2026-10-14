"""Shared release primitives; no cloud credentials or working-tree source reads."""

import hashlib
import json
import os
import re
import stat
import subprocess
import tempfile
import zipfile
import zlib
from email.parser import BytesParser
from pathlib import Path, PurePosixPath

UV_VERSION = "0.12.2"
PYTHON_VERSION = "3.12.12"
WHEEL_PLATFORM = "x86_64-manylinux_2_28"
COMPONENT = "video-serverless-api"
REPOSITORY = "makewheels/video-2022"
PREFIX = "serverless/src/video_serverless/"
TOOLS = ("release_support.py", "build_release.py", "verify_release.py", "smoke_release.py", "check_reproducible.py")
ENTRYPOINTS = {"handler": "video_serverless.fc_handler.handler", "web": "video_serverless.web:app"}
EXCLUDED = {
    "handler": {"web.py", "adapters/asgi.py", "benchmark.py", "startup.py"},
    "web": {"fc_handler.py", "adapters/fc.py", "benchmark.py", "startup.py"},
}
MAX_SIZE = 256 * 1024 * 1024


class ReleaseError(ValueError):
    pass


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def file_hash(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def git(repo: Path, *args: str) -> bytes:
    result = subprocess.run(
        ["git", "--no-optional-locks", "-C", str(repo), *args], capture_output=True, check=False,
    )
    if result.returncode:
        raise ReleaseError("Git operation failed: " + args[0])
    return result.stdout


def clean_head(repo: Path) -> str:
    if git(repo, "status", "--porcelain=v1", "--untracked-files=all").strip():
        raise ReleaseError("Build requires a clean Git checkout, including untracked files")
    commit = git(repo, "rev-parse", "HEAD").decode().strip()
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ReleaseError("Expected a full Git SHA-1 commit")
    return commit


def safe_name(name: str) -> bool:
    path = PurePosixPath(name)
    return bool(name) and bool(path.parts) and (
        not path.is_absolute() and str(path) == name and ".." not in path.parts
        and "\\" not in name and ":" not in name and not any(ord(c) < 32 for c in name)
    )


def source_inputs(repo: Path, commit: str, flavor: str) -> dict[str, bytes]:
    if flavor not in ENTRYPOINTS or not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ReleaseError("Invalid flavor or full commit")
    paths = ["serverless/pyproject.toml", "serverless/uv.lock", PREFIX, "serverless/release/"]
    entries = git(repo, "ls-tree", "-r", "-z", commit, "--", *paths).split(b"\0")
    inputs = {}
    required = {"serverless/pyproject.toml", "serverless/uv.lock"}
    required.update("serverless/release/" + name for name in TOOLS)
    for entry in filter(None, entries):
        meta, raw_path = entry.split(b"\t", 1)
        mode, kind, _ = meta.split()
        path = raw_path.decode("utf-8")
        if path not in required and not path.startswith(PREFIX):
            continue
        if mode not in {b"100644", b"100755"} or kind != b"blob" or not safe_name(path):
            raise ReleaseError("Only regular Git source files are allowed")
        if path.startswith(PREFIX):
            relative = path.removeprefix(PREFIX)
            if not relative.endswith(".py"):
                raise ReleaseError("Runtime source allowlist only accepts Python files")
            if relative in EXCLUDED[flavor]:
                continue
        inputs[path] = git(repo, "show", commit + ":" + path)
    if not required <= inputs.keys() or PREFIX + "__init__.py" not in inputs:
        raise ReleaseError("Required source/build inputs are missing from Git")
    return inputs


def source_payload(inputs: dict[str, bytes]) -> dict[str, bytes]:
    return {"video_serverless/" + name.removeprefix(PREFIX): data
            for name, data in inputs.items() if name.startswith(PREFIX)}


def canonical_zip(payload: dict[str, bytes], output: Path):
    if len(payload) > 20_000 or sum(map(len, payload.values())) > MAX_SIZE:
        raise ReleaseError("Package exceeds project artifact limits")
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(payload.items()):
            if not safe_name(name):
                raise ReleaseError("Unsafe archive name")
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.create_system = 3
            info.external_attr = (stat.S_IFREG | 0o644) << 16
            archive.writestr(info, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)


def read_zip(path: Path) -> dict[str, bytes]:
    try:
        return _read_zip(path)
    except (zipfile.BadZipFile, zlib.error, RuntimeError, EOFError) as error:
        raise ReleaseError("Invalid or corrupt ZIP archive") from error


def _read_zip(path: Path) -> dict[str, bytes]:
    if path.stat().st_size > MAX_SIZE:
        raise ReleaseError("Archive exceeds project limits")
    with zipfile.ZipFile(path) as archive:
        infos = archive.infolist()
        if len(infos) > 20_000 or sum(i.file_size for i in infos) > MAX_SIZE:
            raise ReleaseError("Expanded archive exceeds project limits")
        names = [i.filename for i in infos]
        if len(names) != len(set(names)):
            raise ReleaseError("Duplicate archive entry")
        for info in infos:
            mode = info.external_attr >> 16
            if not safe_name(info.filename) or info.is_dir() or stat.S_IFMT(mode) not in {0, stat.S_IFREG}:
                raise ReleaseError("Unsafe archive entry")
            if info.flag_bits & 1 or info.compress_type not in {zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED}:
                raise ReleaseError("Unsupported archive encoding")
        return {info.filename: archive.read(info) for info in infos}


def dependency_inventory(payload: dict[str, bytes]) -> list[dict[str, str]]:
    dependencies = []
    seen = set()
    for name, data in sorted(payload.items()):
        parts = PurePosixPath(name).parts
        if len(parts) != 2 or not parts[0].endswith(".dist-info") or parts[1] != "METADATA":
            continue
        metadata = BytesParser().parsebytes(data, headersonly=True)
        package, version = metadata.get("Name"), metadata.get("Version")
        if (len(metadata.get_all("Name", [])) != 1 or len(metadata.get_all("Version", [])) != 1
                or not package or not version or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*", package)
                or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9.!+_-]*", version)):
            raise ReleaseError("Invalid dependency metadata")
        normalized = re.sub(r"[-_.]+", "-", package).lower()
        if normalized in seen:
            raise ReleaseError("Duplicate dependency metadata")
        seen.add(normalized)
        dependencies.append({"name": package, "version": version})
    return sorted(dependencies, key=lambda dependency: dependency["name"].lower())


def temporary_output(output: Path, repo: Path):
    root = Path(tempfile.gettempdir()).resolve()
    if (not output.is_relative_to(root) or output == root or output.is_relative_to(repo)
            or repo.is_relative_to(output) or output.exists() or not output.parent.is_dir()):
        raise ReleaseError("Output must be a new directory in OS temp, outside the checkout")


def ci_context() -> dict:
    if os.environ.get("GITHUB_ACTIONS") != "true":
        return {"provider": "local"}
    run_id = os.environ.get("GITHUB_RUN_ID", "")
    attempt = os.environ.get("GITHUB_RUN_ATTEMPT", "")
    if os.environ.get("GITHUB_REPOSITORY") != REPOSITORY or not run_id.isdigit() or not attempt.isdigit():
        raise ReleaseError("Unexpected CI identity")
    return {"provider": "github-actions", "runId": run_id, "attempt": attempt,
            "url": f"https://github.com/{REPOSITORY}/actions/runs/{run_id}"}


def write_json(path: Path, value: dict):
    path.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
