import io
import json
import sys
from pathlib import Path
from types import SimpleNamespace

import pytest

oss2 = pytest.importorskip("oss2", reason="Deployment SDK group is required for storage contracts")
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "deploy"))
from cloud_transport import CloudError, Objects  # noqa: E402
from release_plan import BUCKET, PREFIX, DeploymentError  # noqa: E402


def versioning_xml(state=None, namespace=True):
    xmlns = ' xmlns="http://doc.oss-cn-hangzhou.aliyuncs.com"' if namespace else ""
    content = "" if state is None else "<Status>" + state + "</Status>"
    return ("<VersioningConfiguration" + xmlns + ">" + content + "</VersioningConfiguration>").encode()


def objects(body=None, readback=b"fixture-package"):
    client = Objects("fixture-key", "fixture-secret")
    calls = []
    xml = versioning_xml() if body is None else body
    def raw_request(method, params):
        assert method == "GET" and params == {"versioning": ""}
        return SimpleNamespace(status=200, read=lambda size: xml[:size])
    client.bucket = SimpleNamespace(**{
        "_Bucket__do_bucket": raw_request,
        "put_object": lambda key, data, headers: calls.append((key, data, headers)),
        "get_object": lambda key: io.BytesIO(readback),
    })
    return client, calls


@pytest.mark.parametrize("state", [None, "Enabled", "Suspended"])
def test_locked_sdk_parser_can_miss_namespaced_status(state):
    result = SimpleNamespace(status="unexpected")
    oss2.xml_utils.parse_get_bucket_versioning(result, versioning_xml(state))
    assert result.status is None  # A public SDK result alone is unsafe for this guard.


@pytest.mark.parametrize("namespace", [False, True])
@pytest.mark.parametrize("state", [None, "Enabled", "Suspended", "unknown", ""])
def test_raw_protocol_accepts_only_absent_status(state, namespace):
    client, calls = objects(versioning_xml(state, namespace))
    if state is None:
        client.immutable_put(PREFIX + "fixture.zip", b"fixture-package")
        assert len(calls) == 1
    else:
        with pytest.raises(DeploymentError, match="unversioned"):
            client.immutable_put(PREFIX + "fixture.zip", b"fixture-package")
        assert not calls


@pytest.mark.parametrize("body", [
    b"not xml", b"<WrongRoot/>", b"<VersioningConfiguration>unexpected</VersioningConfiguration>",
    b'<VersioningConfiguration xmlns="https://untrusted.invalid"/>',
    b"<VersioningConfiguration><Other/></VersioningConfiguration>",
    b'<VersioningConfiguration Status="Enabled"/>',
    b'<!DOCTYPE x [<!ENTITY value "private">]><VersioningConfiguration/>',
    b"<VersioningConfiguration/>" + b" " * 4096,
])
def test_malformed_or_oversized_state_never_writes(body):
    client, calls = objects(body)
    with pytest.raises(DeploymentError):
        client.immutable_put(PREFIX + "fixture.zip", b"fixture-package")
    assert not calls


def test_unversioned_upload_is_private_forbids_overwrite_and_reads_same_bytes():
    client, calls = objects()
    client.immutable_put(PREFIX + "fixture.zip", b"fixture-package")
    assert len(calls) == 1
    headers = calls[0][2]
    assert headers["x-oss-forbid-overwrite"] == "true"
    assert headers["x-oss-object-acl"] == "private"
    assert len(headers["x-oss-meta-sha256"]) == 64
    client, _ = objects(readback=b"tampered-fixture")
    with pytest.raises(DeploymentError, match="readback mismatch"):
        client.immutable_put(PREFIX + "fixture.zip", b"fixture-package")


def test_versioning_read_failure_is_redacted_and_has_operation_context():
    client, calls = objects()
    class Denied(Exception):
        code = "AccessDenied"
    def fail(*args, **kwargs):
        raise Denied("fixture-secret-user-credential")
    client.bucket._Bucket__do_bucket = fail
    with pytest.raises(CloudError) as error:
        client.immutable_put(PREFIX + "fixture.zip", b"fixture-package")
    assert str(error.value) == "Cloud operation rejected (oss:GetBucketVersioning): AccessDenied"
    assert "fixture-secret" not in str(error.value)
    assert not calls


@pytest.mark.parametrize("operation", ["get", "put"])
def test_object_failure_has_exact_operation_without_exposing_sdk_request(operation):
    client, _ = objects()

    class Denied(Exception):
        code = "AccessDenied"

    def fail(*args, **kwargs):
        raise Denied("fixture-secret-user-credential")

    if operation == "get":
        client.bucket.get_object = fail
        name = "GetObject"
    else:
        client.bucket.put_object = fail
        name = "PutObject"
    with pytest.raises(CloudError) as error:
        if operation == "get":
            client.get(PREFIX + "fixture.zip")
        else:
            client.immutable_put(PREFIX + "fixture.zip", b"fixture-package")
    assert str(error.value) == "Cloud operation rejected (oss:" + name + "): AccessDenied"
    assert "fixture-secret" not in str(error.value)


def test_production_key_is_rejected_before_any_cloud_request():
    client, calls = objects()
    def should_not_query(*args, **kwargs):
        raise AssertionError("Cloud request executed for out-of-scope key")
    client.bucket._Bucket__do_bucket = should_not_query
    with pytest.raises(DeploymentError, match="outside isolated"):
        client.immutable_put("production/releases/fixture.zip", b"fixture-package")
    assert not calls


def test_policy_has_only_required_actions_and_exact_isolated_resources():
    root = Path(__file__).parents[1] / "deploy"
    policy = json.loads((root / "oss-release-policy.json").read_text())
    actions = {action for statement in policy["Statement"] for action in statement["Action"]}
    assert actions == {"oss:GetBucketVersioning", "oss:GetObject", "oss:PutObject"}
    for statement in policy["Statement"]:
        assert statement["Effect"] == "Allow"
        expected = f"acs:oss:*:*:{BUCKET}" if statement["Action"] == ["oss:GetBucketVersioning"] else (
            f"acs:oss:*:*:{BUCKET}/{PREFIX}*"
        )
        assert statement["Resource"] == [expected]
    trust = json.loads((root / "oss-release-trust.template.json").read_text())
    assert trust["Statement"] == [{"Effect": "Allow", "Action": "sts:AssumeRole", "Principal": {
        "RAM": ["acs:ram::<ACCOUNT_ID>:user/video-2022-oss-data-dev"]
    }}]
