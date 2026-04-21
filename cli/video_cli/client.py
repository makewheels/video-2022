"""HTTP client for video-2022 API."""
import requests
from .config import get_base_url, get_token

REQUEST_ERROR_CODE = 2
INVALID_RESPONSE_CODE = 3


class APIError(Exception):
    def __init__(self, code: int, message: str):
        self.code = code
        self.message = message
        super().__init__(f"API Error [{code}]: {message}")


def _headers(token: str | None = None) -> dict:
    headers = {"Content-Type": "application/json"}
    t = token or get_token()
    if t:
        headers["token"] = t
    return headers


def _handle_response(resp: requests.Response) -> dict:
    try:
        resp.raise_for_status()
    except requests.HTTPError as exc:
        try:
            data = resp.json()
        except ValueError:
            data = None

        if isinstance(data, dict):
            message = data.get("message") or f"HTTP {resp.status_code}"
            code = data.get("code", resp.status_code or REQUEST_ERROR_CODE)
        else:
            detail = (resp.text or "").strip()
            if detail:
                detail = detail[:200]
                message = f"HTTP {resp.status_code}: {detail}"
            else:
                message = f"HTTP {resp.status_code}: {resp.reason or 'request failed'}"
            code = resp.status_code or REQUEST_ERROR_CODE
        raise APIError(code, message) from exc

    try:
        data = resp.json()
    except ValueError as exc:
        raise APIError(INVALID_RESPONSE_CODE, f"Invalid JSON response from {resp.url}") from exc

    if not isinstance(data, dict):
        raise APIError(INVALID_RESPONSE_CODE, f"Unexpected response shape from {resp.url}")

    if data.get("code") != 0:
        raise APIError(data.get("code", -1), data.get("message", "Unknown error"))
    return data.get("data")


def _request(method: str, path: str, *, params: dict | None = None,
             json_data: dict | None = None, base_url: str | None = None,
             token: str | None = None) -> dict:
    url = (base_url or get_base_url()) + path
    try:
        resp = requests.request(
            method,
            url,
            params=params,
            json=json_data,
            headers=_headers(token),
            timeout=30,
        )
    except requests.Timeout as exc:
        raise APIError(REQUEST_ERROR_CODE, f"Request timed out: {url}") from exc
    except requests.ConnectionError as exc:
        raise APIError(REQUEST_ERROR_CODE, f"Could not connect to {url}") from exc
    except requests.RequestException as exc:
        raise APIError(REQUEST_ERROR_CODE, f"Request failed: {exc}") from exc
    return _handle_response(resp)


def get(path: str, params: dict | None = None, base_url: str | None = None, token: str | None = None) -> dict:
    return _request("GET", path, params=params, base_url=base_url, token=token)


def post(path: str, json_data: dict | None = None, base_url: str | None = None, token: str | None = None) -> dict:
    return _request("POST", path, json_data=json_data, base_url=base_url, token=token)
