"""Legacy OSS V1 signing and STS RPC, using standard-library HTTPS with bounded calls."""

import base64
import hashlib
import hmac
import json
from datetime import UTC, datetime
from email.utils import format_datetime
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode
from urllib.request import Request, urlopen
from uuid import uuid4

from video_serverless.ports.avatars import UploadCredentials


class ObjectStorageError(RuntimeError):
    pass


def _signature(secret, value):
    return base64.b64encode(hmac.new(secret.encode(), value.encode(), hashlib.sha1).digest()).decode()


def _https(method, url, headers):
    try:
        with urlopen(Request(url, headers=headers, method=method), timeout=3) as response:
            return response.status, response.read()
    except HTTPError as error:
        # The caller only needs status; never include credential-bearing response bodies in errors.
        error.close()
        return error.code, b""


class OssAvatars:
    def __init__(self, *, bucket, endpoint, access_key_id, secret_key, role_arn, transport=_https,
                 clock=None, nonce=None):
        self._bucket = bucket
        self._endpoint = endpoint
        self._host = endpoint.removeprefix("https://").removeprefix("http://").rstrip("/")
        self._access_key_id = access_key_id
        self._secret_key = secret_key
        self._role_arn = role_arn
        self._transport = transport
        self._clock = clock or (lambda: datetime.now(UTC))
        self._nonce = nonce or (lambda: uuid4().hex)

    def _object_url(self, key):
        return "https://" + self._bucket + "." + self._host + "/" + quote(key, safe="/~")

    def sign(self, key, validity_seconds):
        expires = str(int(self._clock().timestamp()) + validity_seconds)
        value = "GET\n\n\n" + expires + "\n/" + self._bucket + "/" + key
        signature = _signature(self._secret_key, value)
        return self._object_url(key) + "?" + urlencode({
            "Expires": expires, "OSSAccessKeyId": self._access_key_id, "Signature": signature,
        }, quote_via=quote)

    def exists(self, key):
        date = format_datetime(self._clock().astimezone(UTC), usegmt=True)
        value = "HEAD\n\n\n" + date + "\n/" + self._bucket + "/" + key
        headers = {"Date": date, "Authorization": "OSS " + self._access_key_id + ":" +
                   _signature(self._secret_key, value)}
        try:
            status, _ = self._transport("HEAD", self._object_url(key), headers)
        except (URLError, TimeoutError, OSError):
            raise ObjectStorageError("OSS object lookup failed") from None
        if status == 404:
            return False
        if status != 200:
            raise ObjectStorageError("OSS object lookup failed")
        return True

    def upload_credentials(self, key):
        parameters = {
            "Action": "AssumeRole", "Version": "2015-04-01", "Format": "JSON",
            "AccessKeyId": self._access_key_id, "SignatureMethod": "HMAC-SHA1", "SignatureVersion": "1.0",
            "SignatureNonce": self._nonce(), "Timestamp": self._clock().astimezone(UTC).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "RoleArn": self._role_arn, "RoleSessionName": "roleSessionName-" + self._nonce(),
            "DurationSeconds": "10800",
        }
        canonical = urlencode(sorted(parameters.items()), quote_via=quote, safe="~")
        value = "GET&%2F&" + quote(canonical, safe="~")
        parameters["Signature"] = _signature(self._secret_key + "&", value)
        url = "https://sts.cn-beijing.aliyuncs.com/?" + urlencode(parameters, quote_via=quote, safe="~")
        try:
            status, body = self._transport("GET", url, {})
        except (URLError, TimeoutError, OSError):
            # Match the legacy caught STS ClientException -> null -> business error34 path.
            return None
        if status != 200:
            return None
        try:
            credentials = json.loads(body)["Credentials"]
            return UploadCredentials(self._bucket, key, self._endpoint, credentials["AccessKeyId"],
                                     credentials["AccessKeySecret"], credentials["SecurityToken"],
                                     credentials["Expiration"])
        except (ValueError, TypeError, KeyError):
            raise ObjectStorageError("STS credential response is invalid") from None
