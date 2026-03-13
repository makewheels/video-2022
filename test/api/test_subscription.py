"""E2E API tests for subscription management.

Covers: subscribe, unsubscribe, getStatus, getMySubscriptions
"""

import pytest

pytestmark = pytest.mark.api


def test_cannot_subscribe_self(api_client):
    """Cannot subscribe to own channel."""
    resp = api_client.get(f"{api_client.base_url}/user/getMyProfile")
    user_id = resp.json()["data"]["id"]

    resp2 = api_client.get(
        f"{api_client.base_url}/subscription/subscribe",
        params={"channelUserId": user_id},
    )
    body = resp2.json()
    assert body["code"] != 0


def test_subscribe_and_unsubscribe(api_client, second_api_client):
    """Subscribe to another user's channel, then unsubscribe."""
    # Get second user's ID
    resp = second_api_client.get(f"{second_api_client.base_url}/user/getMyProfile")
    channel_user_id = resp.json()["data"]["id"]

    # Subscribe
    resp2 = api_client.get(
        f"{api_client.base_url}/subscription/subscribe",
        params={"channelUserId": channel_user_id},
    )
    assert resp2.json()["code"] == 0

    # Check status
    resp3 = api_client.get(
        f"{api_client.base_url}/subscription/getStatus",
        params={"channelUserId": channel_user_id},
    )
    assert resp3.json()["code"] == 0
    assert resp3.json()["data"] is True

    # Unsubscribe
    resp4 = api_client.get(
        f"{api_client.base_url}/subscription/unsubscribe",
        params={"channelUserId": channel_user_id},
    )
    assert resp4.json()["code"] == 0

    # Check status again
    resp5 = api_client.get(
        f"{api_client.base_url}/subscription/getStatus",
        params={"channelUserId": channel_user_id},
    )
    assert resp5.json()["data"] is False


def test_subscribe_nonexistent_channel(api_client):
    """Subscribing to non-existent channel fails."""
    resp = api_client.get(
        f"{api_client.base_url}/subscription/subscribe",
        params={"channelUserId": "nonexistent_xxx"},
    )
    body = resp.json()
    assert body["code"] != 0


def test_get_my_subscriptions(api_client):
    """Get my subscriptions returns a list."""
    resp = api_client.get(
        f"{api_client.base_url}/subscription/getMySubscriptions",
        params={"skip": 0, "limit": 10},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] == 0
    assert isinstance(body["data"], list)


def test_subscription_requires_auth(base_url):
    """Subscription endpoints require authentication."""
    import requests
    resp = requests.get(
        f"{base_url}/subscription/subscribe",
        params={"channelUserId": "test"},
    )
    body = resp.json()
    assert body["code"] != 0
