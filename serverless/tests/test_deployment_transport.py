"""Check locked SDK serialization, not a home-made replacement schema."""
import io
import json
import sys
from pathlib import Path
from types import SimpleNamespace

import pytest

pytest.importorskip("alibabacloud_fc20230330", reason="Deployment SDK group is required for these contract tests")
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "deploy"))
from cloud_transport import CloudError, Functions  # noqa: E402
from release_plan import FUNCTION, function_body  # noqa: E402


def test_sdk_constructs_nested_function_code_and_alias_requests():
    functions = Functions("fixture-access", "fixture-secret")
    captured = []
    class Body:
        def to_map(self):
            return {"versionId": "1"}
    def fake(*args):
        captured.append(args)
        return SimpleNamespace(body=Body())
    functions.client = SimpleNamespace(create_function=fake, update_alias=fake, get_function=fake)
    payload = function_body("a" * 40, "b" * 64, "t" * 32, "Zml4dHVyZQ==")
    functions.call("create_function", body=payload)
    # The SDK normalizes an absent tag list to []; all declared fields must survive unchanged.
    assert captured[0][0].to_map()["body"] == {**payload, "tags": []}
    functions.call("update_alias", FUNCTION, "dev", body={"versionId": "1", "additionalVersionWeight": {}})
    assert captured[1][:2] == (FUNCTION, "dev")
    assert captured[1][2].to_map() == {"body": {"versionId": "1", "additionalVersionWeight": {}}}
    functions.call("get_function", FUNCTION, query={"qualifier": "candidate"})
    assert captured[2][1].to_map() == {"qualifier": "candidate"}


def test_sdk_invocation_uses_a_stream_and_detects_function_execution_errors():
    functions = Functions("fixture-access", "fixture-secret")
    def invoke(function_name, request):
        assert function_name == FUNCTION
        assert request.qualifier == "candidate"
        assert json.load(request.body) == {"rawPath": "/healthCheck"}
        return SimpleNamespace(headers={}, body=io.BytesIO(b'{"statusCode":200,"body":"ok 1"}'))
    functions.client = SimpleNamespace(invoke_function=invoke)
    assert functions.invoke("candidate", {"rawPath": "/healthCheck"})["statusCode"] == 200
    functions.client = SimpleNamespace(invoke_function=lambda *args:
        SimpleNamespace(headers={"X-Fc-Error-Type": "UnhandledInvocationError"}, body=io.BytesIO(b"secret")))
    with pytest.raises(CloudError, match="FunctionExecutionFailed"):
        functions.invoke("candidate", {})


def test_sdk_exception_is_redacted_to_structured_code():
    functions = Functions("fixture-access", "fixture-secret")
    class Failure(Exception):
        code = "Forbidden"
    def fail(*args):
        raise Failure("request contained a secret credential")
    functions.client = SimpleNamespace(get_alias=fail)
    with pytest.raises(CloudError) as error:
        functions.call("get_alias", FUNCTION, "dev")
    assert str(error.value) == "Cloud operation rejected: Forbidden"
