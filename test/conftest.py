import os

import pytest
import requests as req


BASE_URL = os.getenv("BASE_URL", "http://localhost:5022")
TEST_PHONE = "19900001111"
TEST_PHONE_2 = "19900002222"
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


# ---------------------------------------------------------------------------
# Login helpers
# ---------------------------------------------------------------------------

def login(base_url, phone=TEST_PHONE, code=TEST_CODE):
    """Perform login flow and return the parsed JSON response."""
    req.get(
        f"{base_url}/user/requestVerificationCode",
        params={"phoneNumber": phone},
    )
    resp = req.get(
        f"{base_url}/user/submitVerificationCode",
        params={"phoneNumber": phone, "verificationCode": code},
    )
    assert resp.status_code == 200
    return resp.json()


@pytest.fixture
def second_api_client(base_url):
    """A separate authenticated session using TEST_PHONE_2."""
    data = login(base_url, phone=TEST_PHONE_2)
    token = data["data"]["token"]
    session = req.Session()
    session.headers.update({"token": token})
    session.base_url = base_url
    return session


# ---------------------------------------------------------------------------
# Resource tracking / cleanup fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
def created_videos(api_client):
    """Track created video IDs for cleanup after each test."""
    video_ids = []
    yield video_ids
    for vid in video_ids:
        try:
            api_client.get(f"{api_client.base_url}/video/delete", params={"videoId": vid})
        except Exception:
            pass


@pytest.fixture
def created_playlists(api_client):
    """Track created playlist IDs for cleanup after each test."""
    playlist_ids = []
    yield playlist_ids
    # No delete API yet — tracked for future cleanup support.


@pytest.fixture
def create_video(api_client, created_videos):
    """Factory fixture: create a test video and register it for cleanup."""
    def _create(video_type="SELF_UPLOAD", filename="test.mp4", size=1024):
        resp = api_client.post(
            f"{api_client.base_url}/video/create",
            json={"videoType": video_type, "rawFilename": filename, "size": size},
        )
        data = resp.json()
        if data.get("code") == "ok" and data.get("data"):
            video_id = data["data"].get("videoId")
            if video_id:
                created_videos.append(video_id)
        return data
    return _create
