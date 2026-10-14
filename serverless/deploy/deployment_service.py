"""Immutable package publication, verified aliases, and explicit rollback."""
import base64
import json
import secrets
import time
from pathlib import Path

from cloud_transport import CloudError
from release_plan import (
    ALIASES,
    FUNCTION,
    PREFIX,
    DeploymentError,
    artifact_keys,
    digest,
    function_body,
    identity,
    json_bytes,
    parse_identity,
    verify_configuration,
)

NOT_FOUND = {"FunctionNotFound", "AliasNotFound", "TriggerNotFound", "ResourceNotFound", "NotFound"}


def optional(functions, operation, *args, **kwargs):
    try:
        return functions.call(operation, *args, **kwargs)
    except CloudError as error:
        if error.code in NOT_FOUND:
            return None
        raise


def alias_version(functions, alias):
    if alias not in ALIASES:
        raise DeploymentError("Alias outside isolated environment")
    result = optional(functions, "get_alias", FUNCTION, alias)
    if result is None:
        return None
    if result.get("additionalVersionWeight"):
        raise DeploymentError("Unexpected weighted alias traffic")
    version = result.get("versionId", "")
    if not version.isdecimal():
        raise DeploymentError("Expected immutable numeric FC version")
    return version


def set_alias(functions, alias, version, description):
    if alias not in ALIASES or not isinstance(version, str) or not version.isdecimal():
        raise DeploymentError("Only isolated aliases and immutable numeric versions are allowed")
    exists = optional(functions, "get_alias", FUNCTION, alias)
    body = {"versionId": version, "description": description, "additionalVersionWeight": {}}
    if exists is None:
        functions.call("create_alias", FUNCTION, body={**body, "aliasName": alias})
    else:
        functions.call("update_alias", FUNCTION, alias, body=body)
    if alias_version(functions, alias) != version:
        raise DeploymentError("Alias readback differs from requested version")


def smoke(functions, version):
    config = functions.call("get_function", FUNCTION, query={"qualifier": version})
    commit, expected_hash = parse_identity(config.get("description"))
    verify_configuration(config, commit, expected_hash)
    token = config["environmentVariables"]["PROTOTYPE_TOKEN"]
    health = functions.invoke(version, {"rawPath": "/healthCheck"})
    empty = functions.invoke(version, {"rawPath": "/_prototype/empty",
                                       "headers": {"X-Prototype-Token": token}})
    forbidden = functions.invoke(version, {"rawPath": "/_prototype/empty"})
    if (health.get("statusCode") != 200 or not health.get("body", "").startswith("ok ")
            or empty.get("statusCode") != 200 or json.loads(empty.get("body", "null")) != {"ok": True}
            or forbidden.get("statusCode") != 403):
        raise DeploymentError("Remote diagnostic smoke failed")


def verify_remote(functions, version, commit, expected_hash):
    config = functions.call("get_function", FUNCTION, query={"qualifier": version})
    verify_configuration(config, commit, expected_hash)
    if digest(functions.code(version)) != expected_hash:
        raise DeploymentError("FC code ZIP SHA256 differs from the trusted artifact")


def wait_ready(functions, commit, expected_hash, sleep=time.sleep):
    for _ in range(60):
        config = functions.call("get_function", FUNCTION, query={})
        state, update = config.get("state"), config.get("lastUpdateStatus")
        if state == "Failed" or update == "Failed":
            raise DeploymentError("FC function update failed")
        if state in {None, "Active"} and update in {None, "Successful"}:
            verify_remote(functions, "LATEST", commit, expected_hash)
            return
        sleep(2)
    raise DeploymentError("FC function update did not become ready")


def ensure_trigger(functions):
    config = {"authType": "function", "methods": ["GET", "POST"]}
    existing = optional(functions, "get_trigger", FUNCTION, "isolated-http")
    if existing is None:
        functions.call("create_trigger", FUNCTION, body={"triggerName": "isolated-http", "triggerType": "http",
            "qualifier": "dev", "description": "Isolated API; RAM authentication; no production domain",
            "triggerConfig": json.dumps(config)})
        existing = functions.call("get_trigger", FUNCTION, "isolated-http")
    remote = json.loads(existing.get("triggerConfig", "null"))
    if (existing.get("qualifier") != "dev" or not isinstance(remote, dict)
            or remote.get("authType") != "function" or sorted(remote.get("methods", [])) != ["GET", "POST"]):
        raise DeploymentError("Existing trigger does not match isolated RAM-auth declaration")
    return (existing.get("httpTrigger") or {}).get("urlInternet")


def publish(functions, objects, directory: Path, manifest: dict, trusted_commit: str, trusted_hash: str):
    if manifest.get("flavor") != "handler" or manifest.get("commit") != trusted_commit:
        raise DeploymentError("S03 only accepts the verified Handler release")
    archive = (directory / "payload.zip").read_bytes()
    if digest(archive) != trusted_hash:
        raise DeploymentError("Untrusted archive bytes")
    metadata = (directory / "manifest.json").read_bytes()
    keys = artifact_keys(trusted_commit, trusted_hash, metadata)
    for name, filename in [("archive", "payload.zip"), ("manifest", "manifest.json"),
                           ("requirements", "requirements.txt")]:
        objects.immutable_put(keys[name], (directory / filename).read_bytes())
    # immutable_put verifies all sidecars; the FC bytes come from OSS, never a second build.
    archive = objects.get(keys["archive"])
    if digest(archive) != trusted_hash:
        raise DeploymentError("OSS ZIP readback digest mismatch")
    previous = alias_version(functions, "dev")
    existing = optional(functions, "get_function", FUNCTION, query={})
    body = function_body(trusted_commit, trusted_hash, secrets.token_urlsafe(32), base64.b64encode(archive).decode())
    if existing is None:
        functions.call("create_function", body=body)
    else:
        # Refuse to adopt a manually-created or unrelated function at the fixed name.
        old_commit, old_hash = parse_identity(existing.get("description"))
        verify_configuration(existing, old_commit, old_hash)
        functions.call("update_function", FUNCTION, body={key: value for key, value in body.items()
                                                          if key != "functionName"})
    functions.call("put_concurrency_config", FUNCTION, body={"reservedConcurrency": 2})
    if functions.call("get_concurrency_config", FUNCTION).get("reservedConcurrency") != 2:
        raise DeploymentError("Function concurrency cap readback mismatch")
    wait_ready(functions, trusted_commit, trusted_hash)
    description = identity(trusted_commit, trusted_hash)
    version = functions.call("publish_function_version", FUNCTION, body={"description": description})["versionId"]
    if not isinstance(version, str) or not version.isdecimal():
        raise DeploymentError("Publish did not return an immutable version")
    verify_remote(functions, version, trusted_commit, trusted_hash)
    set_alias(functions, "candidate", version, description)
    smoke(functions, "candidate")
    try:
        set_alias(functions, "dev", version, description)
        smoke(functions, "dev")
        url = ensure_trigger(functions)
        receipt = {"schemaVersion": 1, "component": "video-serverless-api", "environment": "dev",
                   "function": FUNCTION, "commit": trusted_commit, "sha256": trusted_hash,
                   "version": version, "previousVersion": previous, "objects": keys,
                   "triggerUrl": url, "candidateSmoke": "passed", "devSmoke": "passed"}
        data = json_bytes(receipt)
        key = PREFIX + trusted_commit + "/receipts/" + version + "-" + digest(data) + ".json"
        objects.immutable_put(key, data)
        return {**receipt, "receiptKey": key}
    except Exception:
        if previous is not None:
            old = functions.call("get_function", FUNCTION, query={"qualifier": previous})
            old_commit, old_hash = parse_identity(old.get("description"))
            verify_remote(functions, previous, old_commit, old_hash)
            set_alias(functions, "dev", previous, old["description"])
            smoke(functions, "dev")
        else:
            # First deployment has no old version. Remove only the just-created dev alias.
            if alias_version(functions, "dev") == version:
                functions.call("delete_alias", FUNCTION, "dev")
        raise


def rollback(functions, receipt):
    if (receipt.get("component") != "video-serverless-api" or receipt.get("environment") != "dev"
            or receipt.get("function") != FUNCTION):
        raise DeploymentError("Receipt does not identify the isolated release")
    current = alias_version(functions, "dev")
    if current != receipt.get("version"):
        raise DeploymentError("Rollback receipt is not the current dev release")
    previous = receipt.get("previousVersion")
    if not isinstance(previous, str) or not previous.isdecimal() or previous == current:
        raise DeploymentError("Release has no distinct previous version to restore")
    config = functions.call("get_function", FUNCTION, query={"qualifier": previous})
    commit, expected_hash = parse_identity(config.get("description"))
    verify_remote(functions, previous, commit, expected_hash)
    set_alias(functions, "dev", previous, config["description"])
    smoke(functions, "dev")
    return {"function": FUNCTION, "fromVersion": current, "restoredVersion": previous,
            "commit": commit, "sha256": expected_hash, "smoke": "passed"}
