"""End-to-end API tests for search functionality."""

import pytest
import requests


@pytest.mark.api
def test_public_search_with_keyword(base_url):
    """Public video search with a keyword should return results with list and total."""
    resp = requests.get(
        f"{base_url}/video/getPublicVideoList",
        params={"skip": 0, "limit": 10, "keyword": "test"},
    )
    body = resp.json()
    assert body["code"] == 0
    assert "list" in body["data"]
    assert "total" in body["data"]


@pytest.mark.api
def test_my_videos_search_with_keyword(api_client):
    """Searching own videos with a nonexistent keyword should return zero results."""
    resp = api_client.get(
        f"{api_client.base_url}/video/getMyVideoList",
        params={"skip": 0, "limit": 10, "keyword": "nonexistent_xyz"},
    )
    body = resp.json()
    assert body["code"] == 0
    assert body["data"]["total"] == 0


@pytest.mark.api
def test_search_empty_keyword(base_url):
    """Searching with an empty keyword should succeed."""
    resp = requests.get(
        f"{base_url}/video/getPublicVideoList",
        params={"skip": 0, "limit": 10, "keyword": ""},
    )
    body = resp.json()
    assert body["code"] == 0


@pytest.mark.api
def test_search_special_characters(base_url):
    """Searching with special characters (Chinese) should not crash."""
    resp = requests.get(
        f"{base_url}/video/getPublicVideoList",
        params={"skip": 0, "limit": 10, "keyword": "中文"},
    )
    body = resp.json()
    assert body["code"] == 0
