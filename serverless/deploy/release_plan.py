"""Fixed isolation and provenance rules for S03, independent of cloud SDKs."""
import hashlib
import json
import re

REGION = "cn-beijing"
BUCKET = "oss-data-bucket"
PREFIX = "video-2022-dev/serverless/releases/"
FUNCTION = "video-2022-dev-serverless-api"
HANDLER = "video_serverless.fc_handler.handler"
RUNTIME = "python3.12"
ALIASES = {"candidate", "dev"}
IDENTITY = re.compile(r"git=([0-9a-f]{40});sha256=([0-9a-f]{64})")


class DeploymentError(ValueError):
    pass


def digest(data):
    return hashlib.sha256(data).hexdigest()


def identity(commit, sha256):
    value = f"git={commit};sha256={sha256}"
    if not IDENTITY.fullmatch(value):
        raise DeploymentError("A full Git commit and trusted ZIP SHA256 are required")
    return value


def parse_identity(value):
    match = IDENTITY.fullmatch(value or "")
    if not match:
        raise DeploymentError("Function version has no controlled Git/ZIP provenance")
    return match.groups()


def artifact_keys(commit, sha256, manifest_bytes):
    identity(commit, sha256)
    base = PREFIX + commit + "/handler/" + sha256 + "/"
    return {"archive": base + "payload.zip", "manifest": base + "manifest-" + digest(manifest_bytes) + ".json",
            "requirements": base + "requirements.txt"}


def json_bytes(value):
    return (json.dumps(value, sort_keys=True, indent=2) + "\n").encode()


def function_body(commit, sha256, token, archive_base64):
    if not token or len(token) < 32:
        raise DeploymentError("Missing isolated diagnostic token")
    return {
        "functionName": FUNCTION, "runtime": RUNTIME, "handler": HANDLER,
        "description": identity(commit, sha256), "code": {"zipFile": archive_base64},
        "cpu": 0.25, "memorySize": 512, "diskSize": 512, "timeout": 30,
        "instanceConcurrency": 1, "internetAccess": False,
        "environmentVariables": {"PROTOTYPE_TOKEN": token, "TZ": "Asia/Shanghai"},
    }


def verify_configuration(body, commit, sha256):
    expected = {"functionName": FUNCTION, "runtime": RUNTIME, "handler": HANDLER,
                "description": identity(commit, sha256), "cpu": 0.25, "memorySize": 512,
                "diskSize": 512, "timeout": 30, "instanceConcurrency": 1, "internetAccess": False}
    if any(body.get(key) != value for key, value in expected.items()):
        raise DeploymentError("Remote function configuration differs from the isolated declaration")
    env = body.get("environmentVariables") or {}
    if set(env) != {"PROTOTYPE_TOKEN", "TZ"} or len(env.get("PROTOTYPE_TOKEN", "")) < 32:
        raise DeploymentError("Unexpected runtime credentials or Mongo configuration")
    if any(body.get(key) for key in ["vpcConfig", "nasConfig", "ossMountConfig", "layers", "role"]):
        raise DeploymentError("Unexpected network, layer, mount, or runtime role")
