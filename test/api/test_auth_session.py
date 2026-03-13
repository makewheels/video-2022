"""认证与会话管理测试。

验证未登录、无效 token、空 token 均返回 401，
已登录用户可正常访问受保护接口。
"""

import pytest
import requests

from conftest import login, TEST_PHONE, TEST_CODE

pytestmark = pytest.mark.api

BASE = "http://localhost:5022"


# ------------------------------------------------------------------
# 单接口 token 校验
# ------------------------------------------------------------------


def test_invalid_token_returns_401(base_url):
    """使用伪造 token 访问受保护接口，应返回 401。"""
    resp = requests.get(
        f"{base_url}/video/getMyVideoList",
        headers={"token": "fake-token-abc123"},
        allow_redirects=False,
    )
    assert resp.status_code == 401
    body = resp.json()
    assert body["code"] == 401


def test_no_token_returns_401(base_url):
    """不携带 token 请求头，应返回 401。"""
    resp = requests.get(
        f"{base_url}/video/getMyVideoList",
        allow_redirects=False,
    )
    assert resp.status_code == 401
    body = resp.json()
    assert body["code"] == 401


def test_empty_token_returns_401(base_url):
    """携带空字符串 token，应返回 401。"""
    resp = requests.get(
        f"{base_url}/video/getMyVideoList",
        headers={"token": ""},
        allow_redirects=False,
    )
    assert resp.status_code == 401
    body = resp.json()
    assert body["code"] == 401


def test_valid_token_accesses_protected(base_url):
    """使用真实 token 访问受保护接口，应返回 200 且 code==0。"""
    data = login(base_url)
    token = data["data"]["token"]

    resp = requests.get(
        f"{base_url}/video/getMyVideoList",
        params={"skip": 0, "limit": 10},
        headers={"token": token},
        allow_redirects=False,
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] == 0


# ------------------------------------------------------------------
# 所有受保护接口的未认证访问测试
# ------------------------------------------------------------------

PROTECTED_ENDPOINTS = [
    ("POST", "/video/create"),
    ("GET", "/video/rawFileUploadFinish"),
    ("POST", "/video/updateInfo"),
    ("GET", "/video/getMyVideoList"),
    ("GET", "/video/getVideoStatus"),
    ("GET", "/video/delete"),
    ("GET", "/file/getUploadCredentials"),
    ("GET", "/file/uploadFinish"),
    ("GET", "/playlist/getMyPlaylistByPage"),
    ("POST", "/playlist/addPlaylistItem"),
    ("GET", "/videoLike/like"),
    ("GET", "/videoLike/dislike"),
    ("POST", "/comment/add"),
    ("GET", "/comment/delete"),
    ("GET", "/comment/like"),
    ("POST", "/user/updateProfile"),
    ("GET", "/user/getMyProfile"),
    ("GET", "/subscription/subscribe"),
    ("GET", "/subscription/unsubscribe"),
    ("GET", "/subscription/getStatus"),
    ("GET", "/subscription/getMySubscriptions"),
]


@pytest.mark.parametrize("method,path", PROTECTED_ENDPOINTS)
def test_all_protected_endpoints_require_auth(base_url, method, path):
    """未携带 token 访问受保护接口，应全部返回 401。"""
    resp = requests.request(method, f"{base_url}{path}", allow_redirects=False)
    assert resp.status_code == 401, f"{method} {path} should require auth"
