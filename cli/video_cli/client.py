"""HTTP client for video-2022 API."""
import sys
import requests
from .config import get_base_url, get_token


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
    resp.raise_for_status()
    data = resp.json()
    if data.get("code") != 0:
        raise APIError(data.get("code", -1), data.get("message", "Unknown error"))
    return data.get("data")


def get(path: str, params: dict | None = None, base_url: str | None = None, token: str | None = None) -> dict:
    url = (base_url or get_base_url()) + path
    resp = requests.get(url, params=params, headers=_headers(token), timeout=30)
    return _handle_response(resp)


def post(path: str, json_data: dict | None = None, base_url: str | None = None, token: str | None = None) -> dict:
    url = (base_url or get_base_url()) + path
    resp = requests.post(url, json=json_data, headers=_headers(token), timeout=30)
    return _handle_response(resp)
