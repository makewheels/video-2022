import json
from datetime import UTC, datetime
from urllib.error import URLError
from urllib.parse import parse_qs, urlsplit

import pytest

from video_serverless.adapters.oss_avatars import ObjectStorageError, OssAvatars

NOW = datetime(2040, 1, 1, tzinfo=UTC)


def adapter(transport=None):
    return OssAvatars(bucket="fixture-bucket", endpoint="oss-cn-beijing.aliyuncs.com",
                      access_key_id="fixture-access", secret_key="fixture-secret",
                      role_arn="acs:ram::0000000000000000:role/fixture",
                      transport=transport, clock=lambda: NOW, nonce=lambda: "fixture-nonce")


@pytest.mark.parametrize("key,encoded,signature", [
    ("avatar/fixture/file", "avatar/fixture/file", "SVctcazaUJITaO9ftqrpHpJxoHM%3D"),
    ("avatar/中文 空格+?%/file", "avatar/%E4%B8%AD%E6%96%87%20%E7%A9%BA%E6%A0%BC%2B%3F%25/file",
     "YTNv2WPMth7XSHa3Dwd4EbIQiXU%3D"),
])
def test_download_signature_matches_locked_java_oss_3_16_3_probe(key, encoded, signature):
    # Actual Java SDK output with synthetic keys and expiration2040-01-01T00:00:00Z.
    signer = OssAvatars(bucket="fixture-bucket", endpoint="oss-cn-beijing.aliyuncs.com",
                        access_key_id="fixture-access", secret_key="fixture-secret", role_arn="fixture",
                        clock=lambda: datetime(2039, 12, 31, tzinfo=UTC))
    assert signer.sign(key, 86400) == (
        "https://fixture-bucket.oss-cn-beijing.aliyuncs.com/" + encoded +
        "?Expires=2208988800&OSSAccessKeyId=fixture-access&Signature=" + signature
    )


@pytest.mark.parametrize("status,expected", [(200, True), (404, False)])
def test_head_uses_signed_https_and_only_404_means_absent(status, expected):
    calls = []

    def transport(method, url, headers):
        calls.append((method, url, headers))
        return status, b""

    assert adapter(transport).exists("avatar/key") is expected
    method, url, headers = calls[0]
    assert method == "HEAD" and url == "https://fixture-bucket.oss-cn-beijing.aliyuncs.com/avatar/key"
    assert headers["Date"] == "Sun, 01 Jan 2040 00:00:00 GMT"
    assert headers["Authorization"].startswith("OSS fixture-access:")


def test_permission_error_does_not_turn_into_object_absence():
    with pytest.raises(ObjectStorageError, match="OSS object lookup failed"):
        adapter(lambda *args: (403, b"fixture-private-body")).exists("avatar/key")


def test_sts_duration_session_credentials_and_no_cloud_calls():
    calls = []

    def transport(method, url, headers):
        calls.append((method, url, headers))
        return 200, json.dumps({"Credentials": {"AccessKeyId": "fixture-temporary-access",
                                                "AccessKeySecret": "fixture-temporary-secret",
                                                "SecurityToken": "fixture-temporary-session",
                                                "Expiration": "2040-01-01T03:00:00Z"}}).encode()

    credentials = adapter(transport).upload_credentials("avatar/key")
    query = parse_qs(urlsplit(calls[0][1]).query)
    assert calls[0][0] == "GET" and calls[0][1].startswith("https://sts.cn-beijing.aliyuncs.com/")
    assert query["DurationSeconds"] == ["10800"]
    assert query["RoleSessionName"] == ["roleSessionName-fixture-nonce"]
    assert "Signature" in query
    # Actual RpcSignatureComposer from the project's aliyun-java-sdk-core4.6.2.
    assert query["Signature"] == ["Ya5XgS46AmNFymqyIJIvqgRRhR4="]
    assert credentials.key == "avatar/key" and credentials.session_token == "fixture-temporary-session"
    assert "fixture-temporary-secret" not in repr(credentials)


def test_sts_client_failure_maps_to_no_credentials_and_malformed_response_is_fixed():
    assert adapter(lambda *args: (403, b"fixture-private-body")).upload_credentials("key") is None

    def unavailable(*args):
        raise URLError("fixture-private-network-message")

    assert adapter(unavailable).upload_credentials("key") is None
    with pytest.raises(ObjectStorageError, match="^STS credential response is invalid$"):
        adapter(lambda *args: (200, b"fixture-private-body")).upload_credentials("key")
