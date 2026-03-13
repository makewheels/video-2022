import pytest
import responses
from unittest.mock import patch
from video_cli.client import get, post, APIError


class TestClient:
    @responses.activate
    def test_get_success(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/test",
            json={"code": 0, "message": "ok", "data": {"result": "hello"}},
            status=200,
        )
        with patch("video_cli.client.get_base_url", return_value="http://localhost:5022"), \
             patch("video_cli.client.get_token", return_value="t"):
            result = get("/test", base_url="http://localhost:5022", token="test-token")
        assert result == {"result": "hello"}

    @responses.activate
    def test_get_with_params(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/test",
            json={"code": 0, "message": "ok", "data": {"id": "123"}},
            status=200,
        )
        result = get("/test", params={"id": "123"}, base_url="http://localhost:5022", token="t")
        assert result == {"id": "123"}

    @responses.activate
    def test_post_success(self):
        responses.add(
            responses.POST,
            "http://localhost:5022/test",
            json={"code": 0, "message": "ok", "data": {"created": True}},
            status=200,
        )
        result = post("/test", json_data={"name": "test"}, base_url="http://localhost:5022", token="t")
        assert result == {"created": True}

    @responses.activate
    def test_api_error_raised(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/test",
            json={"code": 1, "message": "not found", "data": None},
            status=200,
        )
        with pytest.raises(APIError) as exc:
            get("/test", base_url="http://localhost:5022", token="t")
        assert exc.value.code == 1
        assert "not found" in exc.value.message

    @responses.activate
    def test_http_500_error(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/test",
            status=500,
        )
        with pytest.raises(Exception):
            get("/test", base_url="http://localhost:5022", token="t")

    @responses.activate
    def test_headers_include_token(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/test",
            json={"code": 0, "message": "ok", "data": None},
            status=200,
        )
        get("/test", base_url="http://localhost:5022", token="my-secret-token")
        assert responses.calls[0].request.headers["token"] == "my-secret-token"

    @responses.activate
    def test_null_data_returns_none(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/test",
            json={"code": 0, "message": "ok", "data": None},
            status=200,
        )
        result = get("/test", base_url="http://localhost:5022", token="t")
        assert result is None

    @responses.activate
    def test_post_with_no_body(self):
        responses.add(
            responses.POST,
            "http://localhost:5022/test",
            json={"code": 0, "message": "ok", "data": {}},
            status=200,
        )
        result = post("/test", base_url="http://localhost:5022", token="t")
        assert result == {}

    @responses.activate
    def test_api_error_str(self):
        responses.add(
            responses.GET,
            "http://localhost:5022/test",
            json={"code": 42, "message": "custom error", "data": None},
            status=200,
        )
        with pytest.raises(APIError) as exc:
            get("/test", base_url="http://localhost:5022", token="t")
        assert "42" in str(exc.value)
        assert "custom error" in str(exc.value)

    @responses.activate
    def test_get_uses_config_when_no_args(self):
        responses.add(
            responses.GET,
            "http://example.com:9000/test",
            json={"code": 0, "message": "ok", "data": "ok"},
            status=200,
        )
        with patch("video_cli.client.get_base_url", return_value="http://example.com:9000"), \
             patch("video_cli.client.get_token", return_value="config-token"):
            result = get("/test")
        assert result == "ok"
        assert responses.calls[0].request.headers["token"] == "config-token"
