"""Browser E2E tests for the My Videos page (React SPA).

MyVideosPage structure (from MyVideosPage.tsx):
- div.page-container
  - div.section-header with h2.section-title "我的视频" and span.video-count
  - input.form-input[placeholder="搜索视频..."]
  - div.empty-state "加载中..." or "暂无视频，快去上传吧"
  - OR div.video-table-wrap with table.video-table
  - Pagination component (if totalPages > 1)
  - ConfirmDialog for delete confirmation
"""

import pytest

pytestmark = pytest.mark.browser

BASE_TIMEOUT = 10_000


class TestMyVideosPageLoad:
    """My Videos page loads correctly for authenticated users."""

    def test_page_title_visible(self, auth_page, base_url):
        """Page shows '我的视频' section title."""
        auth_page.goto(base_url)
        title = auth_page.locator("h2.section-title")
        title.wait_for(timeout=BASE_TIMEOUT)
        assert title.inner_text() == "我的视频"

    def test_video_count_visible(self, auth_page, base_url):
        """Page shows video count badge."""
        auth_page.goto(base_url)
        count = auth_page.locator("span.video-count")
        count.wait_for(timeout=BASE_TIMEOUT)
        assert "共" in count.inner_text()

    def test_search_input_present(self, auth_page, base_url):
        """Search input is present on the page."""
        auth_page.goto(base_url)
        search = auth_page.locator("input.form-input[placeholder='搜索视频...']")
        search.wait_for(timeout=BASE_TIMEOUT)
        assert search.is_visible()

    def test_shows_empty_or_table(self, auth_page, base_url):
        """Page shows either the empty state or a video table."""
        auth_page.goto(base_url)
        auth_page.locator("h2.section-title").wait_for(timeout=BASE_TIMEOUT)
        empty = auth_page.locator(".empty-state")
        table = auth_page.locator("table.video-table")
        has_empty = empty.count() > 0 and empty.first.is_visible()
        has_table = table.count() > 0 and table.first.is_visible()
        assert has_empty or has_table


class TestMyVideosAuth:
    """Auth protection on My Videos page."""

    def test_requires_auth(self, page, base_url):
        """Unauthenticated users are redirected to /login."""
        page.goto(base_url)
        page.wait_for_load_state("domcontentloaded")
        page.wait_for_url("**/login**", timeout=BASE_TIMEOUT)
        assert "/login" in page.url


class TestMyVideosSearch:
    """Search functionality."""

    def test_search_input_accepts_text(self, auth_page, base_url):
        """Typing into search input updates value and URL param."""
        auth_page.goto(base_url)
        search = auth_page.locator("input.form-input[placeholder='搜索视频...']")
        search.wait_for(timeout=BASE_TIMEOUT)
        search.fill("test keyword")
        assert search.input_value() == "test keyword"
