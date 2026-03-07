import os

import pytest
import requests as req


BASE_URL = os.getenv("BASE_URL", "http://localhost:5022")
TEST_PHONE = "19900001111"
TEST_CODE = "111"


@pytest.fixture(scope="session")
def base_url():
    return BASE_URL


@pytest.fixture(scope="session")
def auth_token(base_url):
    """Login with test phone number and return token."""
    req.get(
        f"{base_url}/user/requestVerificationCode",
        params={"phoneNumber": TEST_PHONE},
    )
    resp = req.get(
        f"{base_url}/user/submitVerificationCode",
        params={"phoneNumber": TEST_PHONE, "verificationCode": TEST_CODE},
    )
    assert resp.status_code == 200
    data = resp.json()
    return data["data"]["token"]


@pytest.fixture(scope="session")
def api_client(base_url, auth_token):
    """Requests session with auth token pre-configured."""
    session = req.Session()
    session.headers.update({"token": auth_token})
    session.base_url = base_url
    return session


def _api_get(client, path, **kwargs):
    """Helper: GET request using api_client's base_url."""
    return client.get(f"{client.base_url}{path}", **kwargs)


def _api_post(client, path, **kwargs):
    """Helper: POST request using api_client's base_url."""
    return client.post(f"{client.base_url}{path}", **kwargs)
