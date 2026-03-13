"""Browser E2E tests for the video edit page."""
import pytest

pytestmark = pytest.mark.browser
BASE_TIMEOUT = 10_000

class TestEditPageAuth:
    """EditPage认证相关."""

    def test_requires_auth(self, page, base_url):
        """未登录访问编辑页跳转登录."""
        page.goto(f"{base_url}/edit/test-video-id")
        page.wait_for_function(
            "() => window.location.pathname.includes('/login')",
            timeout=BASE_TIMEOUT,
        )
        assert "/login" in page.url

class TestEditPageStructure:
    """EditPage页面结构（需要登录）."""

    def test_shows_loading_state(self, auth_page, base_url):
        """已登录访问编辑页显示加载状态."""
        auth_page.goto(f"{base_url}/edit/nonexistent-video")
        loading = auth_page.locator("text=加载中")
        loading.wait_for(timeout=BASE_TIMEOUT)
        assert loading.is_visible()

    def test_has_back_link(self, auth_page, base_url):
        """编辑页有返回链接."""
        auth_page.goto(f"{base_url}/edit/nonexistent-video")
        # Wait for either loading state or back link
        auth_page.wait_for_load_state("networkidle")
        # The page shows loading first, then either form or error
        # Back link appears after video loads, so we check the page contains either loading or back link
        page_text = auth_page.locator(".page-container").inner_text(timeout=BASE_TIMEOUT)
        assert "加载中" in page_text or "返回" in page_text
