"""
E2E API tests for the login/auth flow.

Covers: verification code login, token validation, user retrieval,
token refresh on re-login, and error handling for bad inputs.
"""

import requests
import pytest

from conftest import login, TEST_CODE

# Use a dedicated phone for login tests to avoid invalidating the
# session-scoped auth_token fixture (which uses TEST_PHONE).
LOGIN_TEST_PHONE = "19900009999"


@pytest.mark.api
def test_login_flow_and_token_validation(base_url):
    """Login with test phone, then use the token to access a protected endpoint."""
    result = login(base_url, LOGIN_TEST_PHONE, TEST_CODE)
    assert result["code"] == 0, f"Login failed: {result}"
    token = result["data"]["token"]

    resp = requests.get(
        f"{base_url}/video/getMyVideoList",
        params={"skip": 0, "limit": 10},
        headers={"token": token},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] == 0, f"getMyVideoList failed: {body}"


@pytest.mark.api
def test_invalid_token_returns_error(base_url):
    """Calling a protected endpoint with a bogus token should return an error code."""
    resp = requests.get(
        f"{base_url}/video/getMyVideoList",
        params={"skip": 0, "limit": 10},
        headers={"token": "invalid_token_xxx"},
    )
    body = resp.json()
    assert body["code"] != 0, f"Expected error but got: {body}"


@pytest.mark.api
def test_get_user_by_token(base_url):
    """Login, then retrieve user info via getUserByToken and verify key fields."""
    result = login(base_url, LOGIN_TEST_PHONE, TEST_CODE)
    assert result["code"] == 0, f"Login failed: {result}"
    token = result["data"]["token"]

    resp = requests.get(
        f"{base_url}/user/getUserByToken",
        params={"token": token},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] == 0, f"getUserByToken failed: {body}"

    user = body["data"]
    assert "phone" in user, f"Missing 'phone' in user data: {user}"
    assert "token" in user, f"Missing 'token' in user data: {user}"
    assert "id" in user, f"Missing 'id' in user data: {user}"


@pytest.mark.api
def test_relogin_refreshes_token(base_url):
    """Logging in twice with the same phone should keep the userId but issue a new token."""
    first = login(base_url, LOGIN_TEST_PHONE, TEST_CODE)
    assert first["code"] == 0, f"First login failed: {first}"

    second = login(base_url, LOGIN_TEST_PHONE, TEST_CODE)
    assert second["code"] == 0, f"Second login failed: {second}"

    assert first["data"]["id"] == second["data"]["id"], "userId should stay the same"
    assert first["data"]["token"] != second["data"]["token"], "Token should be refreshed"


@pytest.mark.api
@pytest.mark.parametrize(
    "phone",
    [
        pytest.param("123", id="invalid_phone_format"),
        pytest.param("", id="empty_phone"),
    ],
)
def test_bad_phone_returns_error(base_url, phone):
    """submitVerificationCode with an invalid or empty phone should return an error."""
    resp = requests.get(
        f"{base_url}/user/submitVerificationCode",
        params={"phone": phone, "code": TEST_CODE},
    )
    body = resp.json()
    assert body["code"] != 0, f"Expected error for phone={phone!r}, got: {body}"


@pytest.mark.api
def test_wrong_verification_code(base_url):
    """Correct phone but wrong verification code should be rejected."""
    requests.get(
        f"{base_url}/user/requestVerificationCode",
        params={"phone": LOGIN_TEST_PHONE},
    )

    resp = requests.get(
        f"{base_url}/user/submitVerificationCode",
        params={"phone": LOGIN_TEST_PHONE, "code": "999"},
    )
    body = resp.json()
    assert body["code"] != 0, f"Expected error for wrong code, got: {body}"
