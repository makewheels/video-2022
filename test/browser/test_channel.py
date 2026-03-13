"""Browser E2E tests for the Channel page (React SPA).

ChannelPage structure:
- div.page-container
  - div.channel-banner (img or placeholder)
  - div.channel-info
    - div.channel-avatar-large
    - div.channel-details
      - h1.channel-name
      - div.channel-stats
      - p.channel-bio
    - button.btn (subscribe/unsubscribe)
  - div.channel-tabs
    - button.channel-tab "视频" (active)
    - button.channel-tab "播放列表"
  - div.yt-grid or div.empty-state

This page is public (no auth required).
"""

import pytest

pytestmark = pytest.mark.browser

BASE_TIMEOUT = 10_000


class TestChannelPageStructure:
    """Channel page renders for a valid user."""

    def test_channel_page_loads(self, page, base_url):
        """Channel page loads without crashing for a fake userId."""
        page.goto(f"{base_url}/channel/fake-user-id")
        page.wait_for_load_state("networkidle", timeout=BASE_TIMEOUT)
        body = page.locator("body")
        assert body.is_visible()

    def test_shows_loading_or_content(self, page, base_url):
        """Page shows loading text or channel content."""
        page.goto(f"{base_url}/channel/fake-user-id")
        loading = page.locator("text=加载中")
        error = page.locator("text=加载频道失败")
        channel_name = page.locator("h1.channel-name")
        loading.or_(error).or_(channel_name).wait_for(timeout=BASE_TIMEOUT)

    def test_header_visible(self, page, base_url):
        """NavBar header is visible on the channel page."""
        page.goto(f"{base_url}/channel/fake-user-id")
        header = page.locator("header.page-header")
        header.wait_for(timeout=BASE_TIMEOUT)
        assert header.is_visible()

    def test_no_auth_required(self, page, base_url):
        """Channel page does not redirect to login."""
        page.goto(f"{base_url}/channel/fake-user-id")
        page.wait_for_load_state("networkidle", timeout=BASE_TIMEOUT)
        assert "/login" not in page.url
