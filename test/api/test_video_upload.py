"""End-to-end API tests for video upload/create workflows."""

import pytest


@pytest.mark.api
def test_create_video(create_video):
    """Creating a video returns expected fields: videoId, fileId, watchUrl, watchId."""
    body = create_video()

    assert body["code"] == 0
    data = body["data"]
    assert "videoId" in data
    assert "fileId" in data
    assert "watchUrl" in data
    assert "watchId" in data


@pytest.mark.api
def test_create_video_appears_in_my_list(api_client, create_video):
    """A newly created video should appear in the user's video list."""
    body = create_video()
    video_id = body["data"]["videoId"]

    list_resp = api_client.get(f"{api_client.base_url}/video/getMyVideoList", params={"skip": 0, "limit": 10})
    assert list_resp.status_code == 200
    list_body = list_resp.json()
    assert list_body["code"] == 0

    video_ids = [v["videoId"] for v in list_body["data"]]
    assert video_id in video_ids, f"Video {video_id} not found in user's video list"


@pytest.mark.api
@pytest.mark.parametrize(
    "payload",
    [
        pytest.param({"videoType": "SELF_UPLOAD", "size": 1024}, id="missing-rawFilename"),
        pytest.param({"videoType": "SELF_UPLOAD", "rawFilename": "test.mp4"}, id="missing-size"),
        pytest.param({}, id="empty-body"),
    ],
)
def test_create_video_missing_fields(api_client, created_videos, payload):
    """Posting with missing required fields should return an error response."""
    resp = api_client.post(f"{api_client.base_url}/video/create", json=payload)
    assert resp.status_code == 200
    body = resp.json()

    # If the server happens to accept the request, track the video for cleanup
    if body["code"] == 0 and "data" in body and "videoId" in body.get("data", {}):
        created_videos.append(body["data"]["videoId"])

    assert body["code"] != 0, f"Expected error for payload {payload}, got success"


@pytest.mark.api
def test_create_video_invalid_type(create_video):
    """Submitting an invalid videoType should still return a valid JSON response."""
    body = create_video(video_type="INVALID_TYPE")

    assert "code" in body, "Response must contain a 'code' field"
