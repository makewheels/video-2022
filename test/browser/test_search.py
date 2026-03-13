"""Browser E2E tests for search functionality."""
import pytest

pytestmark = pytest.mark.browser
BASE_TIMEOUT = 10_000

class TestNavBarSearch:
    """导航栏搜索."""

    def test_search_bar_visible(self, page, base_url):
        """首页搜索栏可见."""
        page.goto(base_url)
        search_input = page.locator(".nav-search input")
        search_input.wait_for(timeout=BASE_TIMEOUT)
        assert search_input.is_visible()

    def test_search_button_visible(self, page, base_url):
        """搜索按钮可见."""
        page.goto(base_url)
        search_btn = page.locator(".nav-search button")
        search_btn.wait_for(timeout=BASE_TIMEOUT)
        assert search_btn.is_visible()
