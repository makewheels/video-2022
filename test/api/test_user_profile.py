"""E2E API tests for user profile management.

Covers: updateProfile, getMyProfile, getChannel
"""

import pytest

pytestmark = pytest.mark.api


def test_get_my_profile(api_client):
    """Get current user profile returns user data."""
    resp = api_client.get(f"{api_client.base_url}/user/getMyProfile")
    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] == 0
    user = body["data"]
    assert "id" in user
    assert "phone" in user


def test_update_profile_nickname(api_client):
    """Update nickname via updateProfile."""
    resp = api_client.post(
        f"{api_client.base_url}/user/updateProfile",
        json={"nickname": "TestUser", "bio": "Test bio"},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] == 0

    # Verify the update
    resp2 = api_client.get(f"{api_client.base_url}/user/getMyProfile")
    user = resp2.json()["data"]
    assert user["nickname"] == "TestUser"
    assert user["bio"] == "Test bio"


def test_update_profile_nickname_too_long(api_client):
    """Nickname longer than 30 chars is rejected."""
    resp = api_client.post(
        f"{api_client.base_url}/user/updateProfile",
        json={"nickname": "x" * 31},
    )
    body = resp.json()
    assert body["code"] != 0


def test_update_profile_bio_too_long(api_client):
    """Bio longer than 200 chars is rejected."""
    resp = api_client.post(
        f"{api_client.base_url}/user/updateProfile",
        json={"bio": "x" * 201},
    )
    body = resp.json()
    assert body["code"] != 0


def test_get_channel(api_client):
    """Get channel info for a user."""
    # First get own user ID
    resp = api_client.get(f"{api_client.base_url}/user/getMyProfile")
    user_id = resp.json()["data"]["id"]

    resp2 = api_client.get(
        f"{api_client.base_url}/user/getChannel",
        params={"userId": user_id},
    )
    assert resp2.status_code == 200
    body = resp2.json()
    assert body["code"] == 0
    channel = body["data"]
    assert channel["userId"] == user_id
    assert "subscriberCount" in channel
    assert "videoCount" in channel


def test_get_channel_nonexistent(api_client):
    """Getting channel for non-existent user returns error."""
    resp = api_client.get(
        f"{api_client.base_url}/user/getChannel",
        params={"userId": "nonexistent_user_id_xxx"},
    )
    body = resp.json()
    assert body["code"] != 0


def test_update_profile_requires_auth(base_url):
    """updateProfile without token fails."""
    import requests
    resp = requests.post(
        f"{base_url}/user/updateProfile",
        json={"nickname": "test"},
    )
    body = resp.json()
    assert body["code"] != 0
