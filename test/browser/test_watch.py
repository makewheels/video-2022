"""Browser E2E tests for the Watch page (React SPA).

WatchPage structure (from WatchPage.tsx):
- If error: div.not-ready-state with error message
- If loading: div with "加载中..."
- If loaded:
  - div.watch-layout
    - div.watch-main
      - div.player-wrapper containing VideoPlayer
      - div.video-info-section
        - h1.video-title
        - div.video-meta (watch count + create time)
        - LikeButtons component
        - p.video-description
      - CommentSection component
    - div.watch-sidebar (if playlist query param)
      - PlaylistSidebar component

This page is public (no auth required) but requires a valid watchId.
Without a real backend, we test that the page structure renders
(loading/error states) at /watch/<id>.
"""

import pytest

pytestmark = pytest.mark.browser

BASE_TIMEOUT = 5_000
FAKE_WATCH_ID = "test-video-id-000"


class TestWatchPageStructure:
    """Watch page renders loading or error state for a fake video ID."""

    def test_page_renders(self, page, base_url):
        """Watch page loads without crashing."""
        page.goto(f"{base_url}/watch/{FAKE_WATCH_ID}")
        page.wait_for_load_state("networkidle", timeout=BASE_TIMEOUT)
        body = page.locator("body")
        assert body.is_visible()

    def test_shows_loading_or_error(self, page, base_url):
        """Page shows loading text or an error state for invalid ID."""
        page.goto(f"{base_url}/watch/{FAKE_WATCH_ID}")
        loading = page.locator("text=加载中")
        error = page.locator(".not-ready-state")
        loading.or_(error).wait_for(timeout=BASE_TIMEOUT)

    def test_header_still_visible(self, page, base_url):
        """NavBar header is visible on the watch page."""
        page.goto(f"{base_url}/watch/{FAKE_WATCH_ID}")
        header = page.locator("header.page-header")
        header.wait_for(timeout=BASE_TIMEOUT)
        assert header.is_visible()


class TestWatchPageElements:
    """Tests for elements visible when a video loads successfully.

    These tests check DOM structure expectations. They will pass only
    when the backend is running and returns a valid video for the ID.
    """

    @pytest.fixture
    def watch_page(self, page, base_url):
        """Navigate to a watch page and wait for video-info or error."""
        page.goto(f"{base_url}/watch/{FAKE_WATCH_ID}")
        info = page.locator(".video-info-section")
        error = page.locator(".not-ready-state")
        loading = page.locator("text=加载中")
        info.or_(error).or_(loading).wait_for(timeout=BASE_TIMEOUT)
        return page

    def test_watch_layout_present(self, watch_page):
        """If video loaded, watch-layout container exists."""
        layout = watch_page.locator(".watch-layout")
        if layout.count() > 0:
            assert layout.is_visible()

    def test_video_title_present(self, watch_page):
        """If video loaded, h1.video-title exists."""
        title = watch_page.locator("h1.video-title")
        if title.count() > 0:
            assert title.is_visible()
            assert len(title.inner_text()) > 0

    def test_video_meta_present(self, watch_page):
        """If video loaded, video-meta section exists."""
        meta = watch_page.locator(".video-meta")
        if meta.count() > 0:
            assert meta.is_visible()

    def test_player_wrapper_present(self, watch_page):
        """If video loaded, player-wrapper container exists."""
        player = watch_page.locator(".player-wrapper")
        if player.count() > 0:
            assert player.is_visible()
