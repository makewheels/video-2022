"""E2E API tests for video watch functionality.

Tests cover watch info retrieval, watch page accessibility,
video detail retrieval, and error handling for nonexistent resources.
"""

import pytest


@pytest.mark.api
def test_get_watch_info(api_client, create_video):
    """Create a video and verify watch info can be retrieved."""
    video = create_video()
    watch_id = video["data"]["watchId"]

    resp = api_client.get(f"{api_client.base_url}/watchController/getWatchInfo", params={"watchId": watch_id})
    assert resp.status_code == 200

    body = resp.json()
    assert body["code"] == 0
    assert body["data"] is not None


@pytest.mark.api
def test_watch_page_accessible(api_client, create_video):
    """Create a video and verify the SPA watch page returns HTTP 200."""
    video = create_video()
    watch_id = video["data"]["watchId"]

    resp = api_client.get(f"{api_client.base_url}/watch/{watch_id}")
    assert resp.status_code == 200


@pytest.mark.api
def test_get_video_detail(api_client, create_video):
    """Create a video and verify video detail contains expected fields."""
    video = create_video()
    video_id = video["data"]["videoId"]

    resp = api_client.get(f"{api_client.base_url}/video/getVideoDetail", params={"videoId": video_id})
    assert resp.status_code == 200

    body = resp.json()
    assert body["code"] == 0

    data = body["data"]
    assert data["id"] is not None
    assert data.get("type") is not None or data.get("videoType") is not None
    assert data["watchId"] is not None


@pytest.mark.api
def test_get_watch_info_nonexistent(api_client):
    """Request watch info for a nonexistent watchId and expect an error."""
    resp = api_client.get(
        f"{api_client.base_url}/watchController/getWatchInfo",
        params={"watchId": "nonexistent_id_999"},
    )
    body = resp.json()
    assert body["code"] != 0


@pytest.mark.api
def test_get_video_detail_nonexistent(api_client):
    """Request video detail for a nonexistent videoId and expect an error."""
    resp = api_client.get(
        f"{api_client.base_url}/video/getVideoDetail",
        params={"videoId": "nonexistent_id_999"},
    )
    body = resp.json()
    assert body["code"] != 0
