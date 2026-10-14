"""Actions-only entry point; no production or credential arguments."""
import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "release"))
from build_release import build  # noqa: E402
from cloud_transport import Functions, Objects  # noqa: E402
from deployment_service import publish, rollback  # noqa: E402
from release_plan import PREFIX, DeploymentError, digest  # noqa: E402
from release_support import REPOSITORY, clean_head, file_hash  # noqa: E402
from verify_release import verify  # noqa: E402


def actions_commit(repo):
    commit = clean_head(repo)
    if (os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("GITHUB_REPOSITORY") != REPOSITORY
            or os.environ.get("GITHUB_REF") != "refs/heads/master" or os.environ.get("GITHUB_SHA") != commit):
        raise DeploymentError("Deployment requires the exact master commit in controlled GitHub Actions")
    return commit


def required_ci(commit):
    result = subprocess.run(["gh", "api", f"repos/{REPOSITORY}/commits/{commit}/check-runs", "--paginate"],
                            check=True, capture_output=True, text=True)
    # --paginate returns one JSON object per page; use the JSON decoder rather than concatenation assumptions.
    decoder, value = json.JSONDecoder(), result.stdout.strip()
    checks = []
    while value:
        page, end = decoder.raw_decode(value)
        checks.extend(page.get("check_runs", []))
        value = value[end:].lstrip()
    gate = [check for check in checks if check.get("name") == "build-and-test"
            and check.get("app", {}).get("slug") == "github-actions"]
    gate.sort(key=lambda check: check.get("started_at", ""), reverse=True)
    if not gate or gate[0].get("conclusion") != "success":
        raise DeploymentError("Latest required CI summary for this exact master commit has not passed")


def clients():
    keys = ["FC_DEPLOY_AK_ID", "FC_DEPLOY_AK_SECRET", "ALIYUN_OSS_DATA_ACCESS_KEY_ID", "ALIYUN_OSS_DATA_SECRET_KEY"]
    if any(not os.environ.get(key) for key in keys):
        raise DeploymentError("Required FC/OSS deployment identity is not injected")
    return (Functions(os.environ[keys[0]], os.environ[keys[1]]),
            Objects(os.environ[keys[2]], os.environ[keys[3]]))


def execute(mode, receipt_key=""):
    repo = Path(__file__).resolve().parents[2]
    commit = actions_commit(repo)
    required_ci(commit)
    functions, objects = clients()
    if mode == "rollback":
        pattern = re.compile(re.escape(PREFIX) + r"([0-9a-f]{40})/receipts/([0-9]+)-([0-9a-f]{64})\.json")
        match = pattern.fullmatch(receipt_key)
        if not match:
            raise DeploymentError("Rollback requires a trusted receipt key from an earlier Actions summary")
        data = objects.get(receipt_key)
        if digest(data) != match[3]:
            raise DeploymentError("Rollback receipt digest mismatch")
        receipt = json.loads(data)
        if receipt.get("commit") != match[1] or receipt.get("version") != match[2]:
            raise DeploymentError("Rollback receipt identity mismatch")
        result = rollback(functions, receipt)
    else:
        root = Path(tempfile.gettempdir()).resolve()
        with tempfile.TemporaryDirectory(prefix="video-serverless-deploy-", dir=root) as name:
            task = Path(name).resolve()
            assert task.parent == root and task.name.startswith("video-serverless-deploy-")
            directory = task / "release"
            manifest = build(repo, directory, "handler")
            expected_hash = file_hash(directory / "payload.zip")
            verify(repo, directory, commit, expected_hash)
            subprocess.run([sys.executable, "-I", "-S", str(repo / "serverless/release/smoke_release.py"),
                            "--directory", str(directory), "--flavor", "handler"], check=True, cwd=task)
            result = publish(functions, objects, directory, manifest, commit, expected_hash)
    print(json.dumps(result, indent=2))
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with Path(summary).open("a") as stream:
            stream.write("### Serverless 隔离环境 " + mode + "\n\n```json\n" + json.dumps(result, indent=2) + "\n```\n")
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=["publish", "rollback"], required=True)
    parser.add_argument("--receipt-key", default="")
    args = parser.parse_args()
    try:
        execute(args.mode, args.receipt_key)
    except Exception as error:
        # Arbitrary SDK/network/secret-provider messages can contain credentials; do not print them.
        message = str(error) if isinstance(error, DeploymentError) else type(error).__name__
        parser.exit(1, "Isolated deployment rejected: " + message + "\n")
