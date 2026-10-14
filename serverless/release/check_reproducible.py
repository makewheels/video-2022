"""Build both flavors twice and smoke only the extracted release bytes."""

import json
import subprocess
import sys
import tempfile
from pathlib import Path

from build_release import build
from release_support import ENTRYPOINTS, ReleaseError, clean_head
from verify_release import verify


def check(repo: Path):
    repo = repo.resolve()
    commit = clean_head(repo)
    root = Path(tempfile.gettempdir()).resolve()
    with tempfile.TemporaryDirectory(prefix="video-release-ci-", dir=root) as name:
        task = Path(name).resolve()
        assert task.parent == root and task.name.startswith("video-release-ci-")
        results = []
        for flavor in ENTRYPOINTS:
            first, second = task / (flavor + "-first"), task / (flavor + "-second")
            manifests = [build(repo, directory, flavor) for directory in [first, second]]
            if manifests[0]["archive"]["sha256"] != manifests[1]["archive"]["sha256"]:
                raise ReleaseError("ZIP is not reproducible: " + flavor)
            for directory, manifest in zip([first, second], manifests, strict=True):
                verify(repo, directory, commit, manifest["archive"]["sha256"])
            # Ignore user site packages and PYTHONPATH: smoke cannot borrow installed dependencies.
            subprocess.run([sys.executable, "-I", "-S", str(repo / "serverless/release/smoke_release.py"),
                            "--directory", str(first), "--flavor", flavor], cwd=task, check=True)
            manifest = manifests[0]
            result = {"commit": commit, "flavor": flavor, "archive": manifest["archive"],
                      "dependencies": manifest["dependencies"], "reproducible": True, "smoke": "passed"}
            results.append(result)
        print(json.dumps(results, indent=2))


if __name__ == "__main__":
    check(Path(__file__).resolve().parents[2])
