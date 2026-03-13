"""End-to-end API tests for video deletion."""

import pytest
import requests


@pytest.mark.api
def test_delete_own_video(api_client, create_video):
    """Deleting own video should succeed and the video should no longer be accessible."""
    data = create_video()
    video_id = data["data"]["videoId"]

    # Delete
    resp = api_client.get(
        f"{api_client.base_url}/video/delete",
        params={"videoId": video_id},
    )
    assert resp.json()["code"] == 0

    # Verify deleted
    resp2 = api_client.get(
        f"{api_client.base_url}/video/getVideoDetail",
        params={"videoId": video_id},
    )
    assert resp2.json()["code"] != 0


@pytest.mark.api
def test_delete_other_user_video(api_client, second_api_client, create_video):
    """Deleting another user's video should return an error."""
    data = create_video()
    video_id = data["data"]["videoId"]

    # Try to delete with second user
    resp = second_api_client.get(
        f"{second_api_client.base_url}/video/delete",
        params={"videoId": video_id},
    )
    body = resp.json()
    assert body["code"] != 0


@pytest.mark.api
def test_delete_nonexistent_video(api_client):
    """Deleting a nonexistent video should return an error."""
    resp = api_client.get(
        f"{api_client.base_url}/video/delete",
        params={"videoId": "nonexistent_xxx"},
    )
    body = resp.json()
    assert body["code"] != 0


@pytest.mark.api
def test_delete_requires_auth(base_url):
    """Deleting a video without authentication should return 401."""
    resp = requests.get(
        f"{base_url}/video/delete",
        params={"videoId": "nonexistent_xxx"},
        allow_redirects=False,
    )
    assert resp.status_code == 401
