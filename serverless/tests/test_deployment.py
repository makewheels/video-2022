"""Isolation, same-byte promotion, and fault recovery without cloud resources."""
import base64
import copy
import json
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "deploy"))
from cloud_transport import CloudError  # noqa: E402
from deployment_service import publish, rollback, set_alias  # noqa: E402
from execute_release import actions_commit, required_ci  # noqa: E402
from release_plan import (  # noqa: E402
    FUNCTION,
    PREFIX,
    DeploymentError,
    artifact_keys,
    digest,
    function_body,
    identity,
    json_bytes,
    verify_configuration,
)

COMMIT = "a" * 40
OLD_COMMIT = "b" * 40
ARCHIVE = b"fixture-zip-bytes"
SHA = digest(ARCHIVE)


class FakeObjects:
    def __init__(self):
        self.objects = {}
        self.corrupt = False

    def immutable_put(self, key, data):
        assert key.startswith(PREFIX)
        if key in self.objects and self.objects[key] != data:
            raise DeploymentError("Immutable conflict")
        self.objects[key] = data

    def get(self, key):
        return b"tamper" if self.corrupt else self.objects[key]


class FakeFunctions:
    def __init__(self, previous=False):
        self.versions = {}
        self.aliases = {}
        self.latest = None
        self.archive = None
        self.calls = []
        self.failed_alias = None
        self.corrupt_code = False
        self.forbidden_status = 403
        self.safe_version = "1" if previous else None
        self.concurrency = None
        self.trigger = None
        if previous:
            old_archive = b"old-fixture"
            self.versions["1"] = (function_body(OLD_COMMIT, digest(old_archive), "s" * 32,
                                              base64.b64encode(old_archive).decode()), old_archive)
            self.latest, self.archive = copy.deepcopy(self.versions["1"])
            self.aliases["dev"] = "1"

    def call(self, operation, *args, body=None, query=None):
        self.calls.append((operation, args, body, query))
        if operation != "create_function":
            assert args[0] == FUNCTION
        if operation == "get_alias":
            if args[1] not in self.aliases:
                raise CloudError("AliasNotFound")
            return {"versionId": self.aliases[args[1]], "additionalVersionWeight": {}}
        if operation in {"create_alias", "update_alias"}:
            alias = body.get("aliasName") or args[1]
            self.aliases[alias] = body["versionId"]
            return {}
        if operation == "delete_alias":
            self.aliases.pop(args[1], None)
            return {}
        if operation == "get_function":
            if self.latest is None:
                raise CloudError("FunctionNotFound")
            version = (query or {}).get("qualifier", "LATEST")
            if version == "LATEST":
                return copy.deepcopy(self.latest)
            version = self.aliases.get(version, version)
            return copy.deepcopy(self.versions[version][0])
        if operation in {"create_function", "update_function"}:
            self.latest = copy.deepcopy(body)
            self.latest["functionName"] = FUNCTION
            self.archive = base64.b64decode(body["code"]["zipFile"])
            return {}
        if operation == "put_concurrency_config":
            self.concurrency = body["reservedConcurrency"]
            return {}
        if operation == "get_concurrency_config":
            return {"reservedConcurrency": self.concurrency}
        if operation == "publish_function_version":
            version = str(len(self.versions) + 1)
            self.versions[version] = (copy.deepcopy(self.latest), self.archive)
            assert body["description"] == self.latest["description"]
            return {"versionId": version}
        if operation == "get_trigger":
            if self.trigger is None:
                raise CloudError("TriggerNotFound")
            return self.trigger
        if operation == "create_trigger":
            self.trigger = copy.deepcopy(body)
            self.trigger["httpTrigger"] = {"urlInternet": "https://isolated.example.invalid"}
            return {}
        raise AssertionError(operation)

    def code(self, version):
        if self.corrupt_code:
            return b"corrupt"
        if version == "LATEST":
            return self.archive
        return self.versions[self.aliases.get(version, version)][1]

    def invoke(self, version, event):
        target = self.aliases.get(version, version)
        if version == self.failed_alias and target != self.safe_version:
            return {"statusCode": 503, "body": "failed"}
        if event["rawPath"] == "/healthCheck":
            return {"statusCode": 200, "body": "ok 123"}
        if not event.get("headers"):
            return {"statusCode": self.forbidden_status, "body": "forbidden"}
        return {"statusCode": 200, "body": '{"ok":true}'}


@pytest.fixture
def artifact(tmp_path):
    files = {"payload.zip": ARCHIVE, "requirements.txt": b"locked", "manifest.json": b'{"flavor":"handler"}'}
    for name, data in files.items():
        (tmp_path / name).write_bytes(data)
    return tmp_path, {"flavor": "handler", "commit": COMMIT}


def release(functions, objects, artifact):
    directory, manifest = artifact
    return publish(functions, objects, directory, manifest, COMMIT, SHA)


def test_promotes_same_version_and_exact_oss_bytes_without_runtime_credentials(artifact):
    functions, objects = FakeFunctions(), FakeObjects()
    receipt = release(functions, objects, artifact)
    assert functions.aliases == {"candidate": "1", "dev": "1"}
    assert functions.archive == objects.get(receipt["objects"]["archive"]) == ARCHIVE
    assert functions.latest["internetAccess"] is False
    assert set(functions.latest["environmentVariables"]) == {"PROTOTYPE_TOKEN", "TZ"}
    assert functions.concurrency == 2
    assert functions.trigger["qualifier"] == "dev"
    assert json.loads(functions.trigger["triggerConfig"])["authType"] == "function"
    assert "PROTOTYPE_TOKEN" not in json_bytes(receipt).decode()
    assert receipt["previousVersion"] is None and receipt["sha256"] == SHA


def test_oss_tampering_prevents_any_fc_call(artifact):
    functions, objects = FakeFunctions(), FakeObjects()
    objects.corrupt = True
    with pytest.raises(DeploymentError, match="OSS"):
        release(functions, objects, artifact)
    assert not functions.calls


def test_package_tampering_before_upload(artifact):
    (artifact[0] / "payload.zip").write_bytes(b"bad")
    objects = FakeObjects()
    with pytest.raises(DeploymentError, match="Untrusted"):
        release(FakeFunctions(), objects, artifact)
    assert not objects.objects


def test_candidate_failure_does_not_promote_dev(artifact):
    functions = FakeFunctions(previous=True)
    functions.failed_alias = "candidate"
    with pytest.raises(DeploymentError, match="smoke"):
        release(functions, FakeObjects(), artifact)
    assert functions.aliases["dev"] == "1"


def test_failed_dev_promotion_restores_previous_version_and_smokes_it(artifact):
    functions = FakeFunctions(previous=True)
    functions.failed_alias = "dev"
    with pytest.raises(DeploymentError, match="smoke"):
        release(functions, FakeObjects(), artifact)
    assert functions.aliases["dev"] == "1" and functions.aliases["candidate"] == "2"


def test_first_failed_deployment_does_not_fabricate_a_previous_version(artifact):
    functions = FakeFunctions()
    functions.failed_alias = "dev"
    with pytest.raises(DeploymentError):
        release(functions, FakeObjects(), artifact)
    assert "dev" not in functions.aliases


def test_remote_zip_tampering_prevents_version_publication(artifact):
    functions = FakeFunctions()
    functions.corrupt_code = True
    with pytest.raises(DeploymentError, match="SHA256"):
        release(functions, FakeObjects(), artifact)
    assert not any(call[0] == "publish_function_version" for call in functions.calls)


def test_unrelated_existing_function_is_not_adopted(artifact):
    functions = FakeFunctions(previous=True)
    functions.latest["description"] = "manually deployed"
    with pytest.raises(DeploymentError, match="provenance"):
        release(functions, FakeObjects(), artifact)
    assert not any(call[0] == "update_function" for call in functions.calls)


def test_unauthenticated_diagnostic_cannot_pass_as_a_valid_release(artifact):
    functions = FakeFunctions()
    functions.forbidden_status = 200
    with pytest.raises(DeploymentError, match="smoke"):
        release(functions, FakeObjects(), artifact)
    assert "dev" not in functions.aliases


def test_explicit_rollback_requires_current_receipt_and_previous_verified_version(artifact):
    functions = FakeFunctions(previous=True)
    receipt = release(functions, FakeObjects(), artifact)
    result = rollback(functions, receipt)
    assert result["restoredVersion"] == "1" and functions.aliases["dev"] == "1"
    with pytest.raises(DeploymentError, match="current"):
        rollback(functions, receipt)


def test_first_release_has_no_explicit_rollback(artifact):
    functions = FakeFunctions()
    receipt = release(functions, FakeObjects(), artifact)
    with pytest.raises(DeploymentError, match="previous"):
        rollback(functions, receipt)


@pytest.mark.parametrize("alias,version", [("prod", "1"), ("dev", "LATEST"), ("dev", 1)])
def test_outside_alias_or_mutable_version_is_rejected_before_any_write(alias, version):
    functions = FakeFunctions()
    with pytest.raises(DeploymentError):
        set_alias(functions, alias, version, "test")
    assert not functions.calls


@pytest.mark.parametrize("mutation", ["mongo", "role", "network", "runtime"])
def test_remote_configuration_cannot_expand_scope(mutation):
    body = function_body(COMMIT, SHA, "t" * 32, "base64")
    if mutation == "mongo":
        body["environmentVariables"]["MONGODB_URI"] = "forbidden"
    elif mutation == "role":
        body["role"] = "unexpected-role"
    elif mutation == "network":
        body["internetAccess"] = True
    else:
        body["runtime"] = "python3.9"
    with pytest.raises(DeploymentError):
        verify_configuration(body, COMMIT, SHA)


def test_keys_bind_both_git_and_zip_identity():
    keys = artifact_keys(COMMIT, SHA, b"manifest")
    assert all(key.startswith(PREFIX + COMMIT + "/handler/" + SHA) for key in keys.values())
    assert digest(b"manifest") in keys["manifest"]
    with pytest.raises(DeploymentError):
        identity("main", SHA)


def test_deployment_rejected_outside_exact_actions_master(monkeypatch, tmp_path):
    import execute_release
    monkeypatch.setattr(execute_release, "clean_head", lambda repo: COMMIT)
    monkeypatch.setenv("GITHUB_ACTIONS", "true")
    monkeypatch.setenv("GITHUB_REPOSITORY", "makewheels/video-2022")
    monkeypatch.setenv("GITHUB_SHA", COMMIT)
    monkeypatch.setenv("GITHUB_REF", "refs/heads/feature")
    with pytest.raises(DeploymentError, match="master"):
        actions_commit(tmp_path)
    monkeypatch.setenv("GITHUB_REF", "refs/heads/master")
    assert actions_commit(tmp_path) == COMMIT


@pytest.mark.parametrize("conclusion", ["failure", None])
def test_ci_requires_latest_successful_github_actions_summary(monkeypatch, conclusion):
    import execute_release
    class Response:
        stdout = json.dumps({"check_runs": [
            {"name": "build-and-test", "app": {"slug": "github-actions"}, "started_at": "old", "conclusion": "success"},
            {"name": "build-and-test", "app": {"slug": "github-actions"}, "started_at": "recent",
             "conclusion": conclusion},
        ]})
    monkeypatch.setattr(execute_release.subprocess, "run", lambda *args, **kwargs: Response())
    with pytest.raises(DeploymentError, match="CI"):
        required_ci(COMMIT)
