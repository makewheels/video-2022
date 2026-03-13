"""End-to-end API tests for error responses."""

import pytest


@pytest.mark.api
def test_nonexistent_video_detail(api_client):
    """Getting detail for a nonexistent video should return an error."""
    resp = api_client.get(
        f"{api_client.base_url}/video/getVideoDetail",
        params={"videoId": "nonexistent_xxx"},
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
def test_update_nonexistent_video(api_client):
    """Updating info for a nonexistent video should return an error."""
    resp = api_client.post(
        f"{api_client.base_url}/video/updateInfo",
        json={"id": "nonexistent_xxx", "title": "test"},
    )
    body = resp.json()
    assert body["code"] != 0


@pytest.mark.api
def test_get_status_nonexistent_video(api_client):
    """Getting status for a nonexistent video should return an error."""
    resp = api_client.get(
        f"{api_client.base_url}/video/getVideoStatus",
        params={"videoId": "nonexistent_xxx"},
    )
    body = resp.json()
    assert body["code"] != 0


@pytest.mark.api
def test_create_video_missing_fields(api_client):
    """Creating a video with an empty body should return an error."""
    resp = api_client.post(
        f"{api_client.base_url}/video/create",
        json={},
    )
    body = resp.json()
    assert body["code"] != 0


@pytest.mark.api
def test_comment_on_nonexistent_video(api_client):
    """Commenting on a nonexistent video should return an error."""
    resp = api_client.post(
        f"{api_client.base_url}/comment/add",
        json={"videoId": "nonexistent_xxx", "content": "test"},
    )
    body = resp.json()
    assert body["code"] != 0
