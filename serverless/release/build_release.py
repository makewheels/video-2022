"""Build a Linux candidate from clean Git objects; never deploys anything."""

import argparse
import json
import os
import platform
import shutil
import subprocess
import sys
import tempfile
import zlib
from pathlib import Path

from release_support import (
    COMPONENT,
    ENTRYPOINTS,
    PYTHON_VERSION,
    REPOSITORY,
    TOOLS,
    UV_VERSION,
    WHEEL_PLATFORM,
    ReleaseError,
    canonical_zip,
    ci_context,
    clean_head,
    dependency_inventory,
    file_hash,
    sha256,
    source_inputs,
    source_payload,
    temporary_output,
    write_json,
)


def check_host():
    if sys.platform != "linux" or platform.machine() != "x86_64" or platform.python_version() != PYTHON_VERSION:
        raise ReleaseError("Build requires Linux x86_64 CPython " + PYTHON_VERSION)
    version = subprocess.check_output(["uv", "--version"], text=True).split()[1]
    if version != UV_VERSION:
        raise ReleaseError("Build requires uv " + UV_VERSION)


def dependency_payload(stage: Path) -> dict[str, bytes]:
    payload = {}
    generated = {"RECORD", "INSTALLER", "REQUESTED", "uv_cache.json", "direct_url.json"}
    for path in sorted(stage.rglob("*")):
        if path.is_symlink():
            raise ReleaseError("Dependency symlinks are not allowed")
        if not path.is_file():
            continue
        relative = path.relative_to(stage)
        if relative.parts[0] == "bin" or "__pycache__" in relative.parts or path.suffix == ".pyc":
            continue
        if any(p.endswith(".dist-info") for p in relative.parts) and path.name in generated:
            continue
        if path.suffix in {".pyd", ".dll", ".dylib", ".pth"}:
            raise ReleaseError("Unexpected native or path-injection dependency file")
        payload[relative.as_posix()] = path.read_bytes()
    if any(name.startswith("video_serverless/") for name in payload):
        raise ReleaseError("A dependency cannot replace application source")
    return payload


def build(repo: Path, output: Path, flavor: str) -> dict:
    check_host()
    repo, output = repo.resolve(), output.resolve()
    temporary_output(output, repo)
    commit = clean_head(repo)
    inputs = source_inputs(repo, commit, flavor)
    for name in TOOLS:
        actual = Path(__file__).parent / name
        expected = repo / "serverless/release" / name
        if actual.resolve() != expected or actual.read_bytes().replace(b"\r\n", b"\n") != inputs[
            "serverless/release/" + name
        ]:
            raise ReleaseError("Builder must execute the committed release tools")
    root = Path(tempfile.gettempdir()).resolve()
    with tempfile.TemporaryDirectory(prefix="video-release-build-", dir=root) as name:
        task = Path(name).resolve()
        assert task.parent == root and task.name.startswith("video-release-build-")
        project = task / "project"
        project.mkdir()
        for filename in ["pyproject.toml", "uv.lock"]:
            (project / filename).write_bytes(inputs["serverless/" + filename])
        env = {k: v for k, v in os.environ.items() if not k.startswith(("UV_", "PIP_")) and k != "PYTHONPATH"}
        env.update(UV_CACHE_DIR=str(task / "cache"), PYTHONDONTWRITEBYTECODE="1", UV_PYTHON_DOWNLOADS="never")
        requirements = task / "requirements.txt"
        command = ["uv", "export", "--project", str(project), "--python", sys.executable, "--locked",
                   "--no-dev", "--no-default-groups", "--no-emit-project", "--no-header", "--no-annotate",
                   "--format", "requirements.txt", "--output-file", str(requirements)]
        if flavor == "web":
            command += ["--extra", "web"]
        subprocess.run(command, env=env, check=True)
        stage = task / "dependencies"
        subprocess.run([
            "uv", "pip", "install", "--python", sys.executable, "--python-platform", WHEEL_PLATFORM,
            "--target", str(stage), "--require-hashes", "--only-binary", ":all:", "--no-deps",
            "--link-mode", "hardlink", "--requirements", str(requirements),
        ], env=env, check=True)
        payload = dependency_payload(stage)
        dependencies = dependency_inventory(payload)
        payload.update(source_payload(inputs))
        payload["release/requirements.txt"] = requirements.read_bytes()
        archive = task / "payload.zip"
        canonical_zip(payload, archive)
        manifest = {
            "schemaVersion": 1, "component": COMPONENT, "repository": REPOSITORY,
            "commit": commit, "flavor": flavor, "entrypoint": ENTRYPOINTS[flavor],
            "target": {"os": "linux", "architecture": "x86_64", "python": PYTHON_VERSION,
                       "wheelPlatform": WHEEL_PLATFORM},
            "builder": {"uv": UV_VERSION, "zlib": zlib.ZLIB_RUNTIME_VERSION}, "ci": ci_context(),
            "inputs": {path: sha256(data) for path, data in sorted(inputs.items())},
            "requirementsSha256": file_hash(requirements), "dependencies": dependencies,
            "archive": {"name": "payload.zip", "sha256": file_hash(archive), "size": archive.stat().st_size},
            "files": {path: sha256(data) for path, data in sorted(payload.items())},
        }
        if clean_head(repo) != commit:
            raise ReleaseError("Git HEAD changed during the build")
        output.mkdir()
        try:
            shutil.copyfile(archive, output / "payload.zip")
            shutil.copyfile(requirements, output / "requirements.txt")
            write_json(output / "manifest.json", manifest)
        except BaseException:
            for filename in ["payload.zip", "requirements.txt", "manifest.json"]:
                (output / filename).unlink(missing_ok=True)
            output.rmdir()
            raise
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--flavor", choices=ENTRYPOINTS, required=True)
    args = parser.parse_args()
    try:
        result = build(args.repo, args.output, args.flavor)
    except (ReleaseError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"Build rejected: {error}\n")
    print(json.dumps({"commit": result["commit"], "flavor": result["flavor"], "archive": result["archive"]}))


if __name__ == "__main__":
    main()
