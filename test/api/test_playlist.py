"""End-to-end API tests for playlist management endpoints.

Covers creating playlists, adding videos, and retrieving playlists by page.
"""

import pytest


pytestmark = pytest.mark.api


@pytest.mark.api
def test_create_playlist_and_add_video(api_client, create_video, created_playlists):
    """Create a video, create a playlist, add the video, and verify via listing."""
    # Create a video
    video_data = create_video()
    assert video_data.get("code") == 0
    video_id = video_data["data"]["videoId"]

    # Create a playlist
    resp = api_client.post(
        f"{api_client.base_url}/playlist/createPlaylist",
        json={"title": "Test Playlist Single", "description": "E2E test playlist"},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] == 0
    playlist_id = body["data"]["id"]
    created_playlists.append(playlist_id)

    # Add video to playlist
    resp = api_client.post(
        f"{api_client.base_url}/playlist/addPlaylistItem",
        json={"playlistId": playlist_id, "videoIdList": [video_id], "addMode": "ADD_TO_TOP"},
    )
    assert resp.status_code == 200
    assert resp.json()["code"] == 0

    # Verify playlist appears in listing
    resp = api_client.get(f"{api_client.base_url}/playlist/getMyPlaylistByPage")
    assert resp.status_code == 200
    listing = resp.json()
    assert listing["code"] == 0

    playlists = listing["data"] if isinstance(listing["data"], list) else listing["data"].get("list", [])
    found = any(p.get("id") == playlist_id for p in playlists)
    assert found, f"Playlist {playlist_id} not found in listing"


@pytest.mark.api
def test_add_multiple_videos(api_client, create_video, created_playlists):
    """Create two videos, add both to a new playlist, and verify success."""
    # Create two videos
    v1 = create_video()
    v2 = create_video()
    assert v1.get("code") == 0 and v2.get("code") == 0
    vid1 = v1["data"]["videoId"]
    vid2 = v2["data"]["videoId"]

    # Create playlist
    resp = api_client.post(
        f"{api_client.base_url}/playlist/createPlaylist",
        json={"title": "Test Playlist Multi", "description": "E2E multi-video test"},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] == 0
    playlist_id = body["data"]["id"]
    created_playlists.append(playlist_id)

    # Add both videos
    resp = api_client.post(
        f"{api_client.base_url}/playlist/addPlaylistItem",
        json={"playlistId": playlist_id, "videoIdList": [vid1, vid2], "addMode": "ADD_TO_TOP"},
    )
    assert resp.status_code == 200
    assert resp.json()["code"] == 0

    # Verify playlist in listing
    resp = api_client.get(f"{api_client.base_url}/playlist/getMyPlaylistByPage")
    assert resp.status_code == 200
    listing = resp.json()
    assert listing["code"] == 0

    playlists = listing["data"] if isinstance(listing["data"], list) else listing["data"].get("list", [])
    match = [p for p in playlists if p.get("id") == playlist_id]
    assert len(match) == 1, f"Playlist {playlist_id} not found in listing"


@pytest.mark.api
def test_create_playlist_empty_name(api_client, created_playlists):
    """Attempt to create a playlist with an empty title — expect a valid JSON response."""
    resp = api_client.post(
        f"{api_client.base_url}/playlist/createPlaylist",
        json={"title": "", "description": "Empty title test"},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert "code" in body, "Response must contain a 'code' field"

    # Track for cleanup if the server accepted the request
    if body["code"] == 0 and isinstance(body.get("data"), dict):
        pid = body["data"].get("id")
        if pid is not None:
            created_playlists.append(pid)


@pytest.mark.api
def test_create_playlist_missing_title(api_client, created_playlists):
    """Attempt to create a playlist without a title field — expect a valid JSON response."""
    resp = api_client.post(
        f"{api_client.base_url}/playlist/createPlaylist",
        json={"description": "No title provided"},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert "code" in body, "Response must contain a 'code' field"

    # Track for cleanup if the server accepted the request
    if body["code"] == 0 and isinstance(body.get("data"), dict):
        pid = body["data"].get("id")
        if pid is not None:
            created_playlists.append(pid)
