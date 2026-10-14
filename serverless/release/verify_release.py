"""Verify against caller-supplied trusted commit/digest, not a self-asserted manifest."""

import argparse
import json
from pathlib import Path

from release_support import (
    COMPONENT, ENTRYPOINTS, PYTHON_VERSION, REPOSITORY, UV_VERSION, WHEEL_PLATFORM,
    ReleaseError, file_hash, read_zip, sha256, source_inputs, source_payload,
)


def verify(repo: Path, directory: Path, commit: str, expected_sha256: str) -> dict:
    path = directory / "manifest.json"
    if path.stat().st_size > 8 * 1024 * 1024:
        raise ReleaseError("Manifest exceeds project limit")
    manifest = json.loads(path.read_text(encoding="utf-8"))
    flavor = manifest["flavor"]
    if (manifest["schemaVersion"] != 1 or manifest["component"] != COMPONENT
            or manifest["repository"] != REPOSITORY or manifest["commit"] != commit
            or flavor not in ENTRYPOINTS or manifest["entrypoint"] != ENTRYPOINTS[flavor]):
        raise ReleaseError("Manifest identity mismatch")
    if manifest["target"] != {"os": "linux", "architecture": "x86_64", "python": PYTHON_VERSION,
                              "wheelPlatform": WHEEL_PLATFORM} or manifest["builder"]["uv"] != UV_VERSION:
        raise ReleaseError("Unexpected build target/tool")
    archive = directory / "payload.zip"
    actual = file_hash(archive)
    if (manifest["archive"]["name"] != "payload.zip" or actual != expected_sha256
            or actual != manifest["archive"]["sha256"] or archive.stat().st_size != manifest["archive"]["size"]):
        raise ReleaseError("Archive digest/size mismatch")
    if file_hash(directory / "requirements.txt") != manifest["requirementsSha256"]:
        raise ReleaseError("Requirements digest mismatch")
    inputs = source_inputs(repo, commit, flavor)
    if manifest["inputs"] != {name: sha256(data) for name, data in inputs.items()}:
        raise ReleaseError("Git input digest mismatch")
    payload = read_zip(archive)
    if manifest["files"] != {name: sha256(data) for name, data in payload.items()}:
        raise ReleaseError("Archive inventory mismatch")
    expected_source = source_payload(inputs)
    actual_source = {name: data for name, data in payload.items() if name.startswith("video_serverless/")}
    if actual_source != expected_source:
        raise ReleaseError("Application bytes do not match the committed source")
    if flavor == "handler" and any(name.startswith(("fastapi/", "starlette/", "uvicorn/")) for name in payload):
        raise ReleaseError("Handler unexpectedly contains Web runtime dependencies")
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--expected-commit", required=True)
    parser.add_argument("--expected-sha256", required=True)
    args = parser.parse_args()
    try:
        verify(args.repo.resolve(), args.directory.resolve(), args.expected_commit, args.expected_sha256)
    except (ReleaseError, OSError, KeyError, TypeError, ValueError) as error:
        parser.exit(1, f"Verification rejected: {error}\n")
    print("Release integrity verified")


if __name__ == "__main__":
    main()
