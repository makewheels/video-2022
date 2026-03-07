"""E2E API tests for video modify operations (updateInfo)."""

import pytest


@pytest.mark.api
def test_update_video_info(api_client, create_video):
    """Create a video, update its title and description, then verify the changes."""
    video = create_video()
    video_id = video["data"]["videoId"]

    resp = api_client.post(
        f"{api_client.base_url}/video/updateInfo",
        json={"id": video_id, "title": "new title", "description": "new desc"},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] == "ok"

    resp = api_client.get(
        f"{api_client.base_url}/video/getVideoDetail",
        params={"videoId": video_id},
    )
    assert resp.status_code == 200
    detail = resp.json()
    assert detail["code"] == "ok"
    assert detail["data"]["title"] == "new title"
    assert detail["data"]["description"] == "new desc"


@pytest.mark.api
def test_update_video_by_other_user_fails(api_client, create_video, second_api_client):
    """Verify that a different user cannot modify another user's video."""
    video = create_video()
    video_id = video["data"]["videoId"]

    resp = second_api_client.post(
        f"{second_api_client.base_url}/video/updateInfo",
        json={"id": video_id, "title": "hacked title", "description": "hacked desc"},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] != "ok"
