"""SDK transport; credentials stay in memory and SDK errors are never printed."""
import io
import json
import urllib.parse
import urllib.request

from release_plan import BUCKET, FUNCTION, PREFIX, REGION, DeploymentError, digest

MAX_BYTES = 256 * 1024 * 1024


class CloudError(DeploymentError):
    def __init__(self, code):
        self.code = code
        super().__init__("Cloud operation rejected: " + str(code))


class Functions:
    def __init__(self, access_key, secret_key):
        from alibabacloud_fc20230330 import models
        from alibabacloud_fc20230330.client import Client
        from alibabacloud_tea_openapi.models import Config
        self.models = models
        self.client = Client(Config(access_key_id=access_key, access_key_secret=secret_key,
                                    endpoint=f"fc.{REGION}.aliyuncs.com", read_timeout=30000, connect_timeout=10000))

    def call(self, operation, *args, body=None, query=None):
        try:
            method = getattr(self.client, operation)
            if body is not None:
                request_name = "".join(word.capitalize() for word in operation.split("_")) + "Request"
                request = getattr(self.models, request_name)().from_map({"body": body})
                args = (*args, request)
            elif query is not None:
                request_name = "".join(word.capitalize() for word in operation.split("_")) + "Request"
                args = (*args, getattr(self.models, request_name)().from_map(query))
            result = method(*args)
            return result.body.to_map() if result.body is not None else {}
        except Exception as error:
            # API messages can include environment values; retain only the structured error code.
            raise CloudError(getattr(error, "code", None) or type(error).__name__) from None

    def invoke(self, version, event):
        try:
            request = self.models.InvokeFunctionRequest(qualifier=version, body=io.BytesIO(json.dumps(event).encode()))
            result = self.client.invoke_function(FUNCTION, request)
            headers = {key.lower(): value for key, value in (result.headers or {}).items()}
            if headers.get("x-fc-error-type"):
                raise CloudError("FunctionExecutionFailed")
            data = result.body.read(MAX_BYTES + 1)
            if len(data) > MAX_BYTES:
                raise CloudError("ResponseTooLarge")
            return json.loads(data)
        except CloudError:
            raise
        except Exception as error:
            raise CloudError(getattr(error, "code", None) or type(error).__name__) from None

    def code(self, version):
        result = self.call("get_function_code", FUNCTION, query={"qualifier": version})
        url = urllib.parse.urlsplit(result["url"])
        if url.scheme not in {"http", "https"} or not (url.hostname or "").endswith(".aliyuncs.com"):
            raise DeploymentError("Unexpected FC code download host")
        secure_url = urllib.parse.urlunsplit(url._replace(scheme="https"))
        # Do not follow a redirect carrying the signed download URL to another host.
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, req, fp, code, msg, headers, newurl):
                return None
        opener = urllib.request.build_opener(NoRedirect)
        with opener.open(secure_url, timeout=30) as response:
            data = response.read(MAX_BYTES + 1)
        if len(data) > MAX_BYTES:
            raise DeploymentError("FC code download exceeds project limit")
        return data


class Objects:
    def __init__(self, access_key, secret_key):
        import oss2
        self.sdk = oss2
        self.bucket = oss2.Bucket(oss2.Auth(access_key, secret_key), f"https://oss-{REGION}.aliyuncs.com", BUCKET)

    def get(self, key):
        if not key.startswith(PREFIX):
            raise DeploymentError("Object key outside isolated release prefix")
        try:
            result = self.bucket.get_object(key)
            data = result.read(MAX_BYTES + 1)
            if len(data) > MAX_BYTES:
                raise DeploymentError("Object exceeds project limit")
            return data
        except DeploymentError:
            raise
        except Exception as error:
            raise CloudError(getattr(error, "code", None) or type(error).__name__) from None

    def immutable_put(self, key, data):
        if not key.startswith(PREFIX):
            raise DeploymentError("Object key outside isolated release prefix")
        try:
            self.bucket.put_object(key, data, headers={"x-oss-forbid-overwrite": "true",
                "x-oss-object-acl": "private", "x-oss-meta-sha256": digest(data)})
        except self.sdk.exceptions.ServerError as error:
            if error.status != 409:
                raise CloudError(error.code) from None
        except Exception as error:
            raise CloudError(getattr(error, "code", None) or type(error).__name__) from None
        if self.get(key) != data:
            raise DeploymentError("Immutable OSS object readback mismatch")
