"""Browser E2E tests for the home page."""
import pytest

pytestmark = pytest.mark.browser
BASE_TIMEOUT = 10_000

class TestHomePageAccess:
    """首页访问."""

    def test_loads_without_auth(self, page, base_url):
        """首页不需要登录即可访问."""
        page.goto(base_url)
        page.wait_for_load_state("networkidle")
        # Should NOT redirect to login
        assert "/login" not in page.url

    def test_shows_empty_state(self, page, base_url):
        """无视频时显示空状态提示."""
        page.goto(base_url)
        page.wait_for_load_state("networkidle")
        empty = page.locator(".empty-state")
        if empty.count() > 0:
            assert "暂无公开视频" in empty.inner_text()

class TestHomePageStructure:
    """首页结构."""

    def test_has_page_container(self, page, base_url):
        """首页有home-page容器."""
        page.goto(base_url)
        container = page.locator(".home-page")
        container.wait_for(timeout=BASE_TIMEOUT)
        assert container.is_visible()

    def test_footer_visible(self, page, base_url):
        """首页footer可见."""
        page.goto(base_url)
        footer = page.locator("footer.page-footer")
        footer.wait_for(timeout=BASE_TIMEOUT)
        assert "Video Platform" in footer.inner_text()
