"""Browser E2E tests for authentication session handling."""
import pytest

pytestmark = pytest.mark.browser
BASE_TIMEOUT = 10_000

class TestExpiredToken:
    """Token过期/无效时的前端处理."""

    def test_expired_token_redirects_to_login(self, page, base_url):
        """无效token访问受保护页面应自动跳转登录页."""
        page.goto(base_url)
        page.evaluate("localStorage.setItem('token', 'expired_token_xxx')")
        page.goto(f"{base_url}/my-videos")
        page.wait_for_function(
            "() => window.location.pathname.includes('/login')",
            timeout=BASE_TIMEOUT,
        )
        assert "/login" in page.url

    def test_redirect_preserves_target(self, page, base_url):
        """跳转登录后URL保留target参数."""
        page.goto(base_url)
        page.evaluate("localStorage.setItem('token', 'expired_token_xxx')")
        page.goto(f"{base_url}/my-videos")
        page.wait_for_function(
            "() => window.location.pathname.includes('/login')",
            timeout=BASE_TIMEOUT,
        )
        assert "target=" in page.url or "my-videos" in page.url

class TestProtectedRoutes:
    """受保护路由未登录时跳转."""

    @pytest.mark.parametrize("path", [
        "/my-videos",
        "/upload",
        "/statistics",
        "/youtube",
        "/settings",
    ])
    def test_protected_route_redirects(self, page, base_url, path):
        """未登录访问受保护路由跳转到登录页."""
        page.goto(f"{base_url}{path}")
        page.wait_for_function(
            "() => window.location.pathname.includes('/login')",
            timeout=BASE_TIMEOUT,
        )
        assert "/login" in page.url
