"""Browser E2E tests for navigation and layout (React SPA).

NavBar structure (from NavBar.tsx):
- header.page-header
  - Link.logo → "📹 Video" → /
  - nav.nav-menu with nav-links: 首页(/), 上传(/upload), 统计(/statistics), YouTube(/youtube)
  - .header-right with theme toggle and auth section
  - button.mobile-menu-btn (hamburger)
- footer.page-footer with "Video Platform"
"""

import pytest

pytestmark = pytest.mark.browser

BASE_TIMEOUT = 5_000


class TestNavBarStructure:
    """NavBar renders with correct structure."""

    def test_logo_visible(self, auth_page, base_url):
        """Logo link '📹 Video' is visible in header."""
        auth_page.goto(base_url)
        logo = auth_page.locator("a.logo")
        logo.wait_for(timeout=BASE_TIMEOUT)
        assert "Video" in logo.inner_text()

    def test_nav_links_present(self, auth_page, base_url):
        """All four nav links are present."""
        auth_page.goto(base_url)
        nav = auth_page.locator("nav.nav-menu")
        nav.wait_for(timeout=BASE_TIMEOUT)
        links = nav.locator("a.nav-link")
        assert links.count() == 4
        labels = [links.nth(i).inner_text() for i in range(4)]
        assert labels == ["首页", "上传", "统计", "YouTube"]

    def test_footer_visible(self, auth_page, base_url):
        """Footer with 'Video Platform' is visible."""
        auth_page.goto(base_url)
        footer = auth_page.locator("footer.page-footer")
        footer.wait_for(timeout=BASE_TIMEOUT)
        assert "Video Platform" in footer.inner_text()


class TestNavigation:
    """Clicking nav links navigates to correct routes."""

    def test_logo_navigates_home(self, auth_page, base_url):
        """Clicking the logo navigates to /."""
        auth_page.goto(f"{base_url}/upload")
        auth_page.locator("a.logo").click()
        auth_page.wait_for_url(f"{base_url}/", timeout=BASE_TIMEOUT)

    def test_upload_link_navigates(self, auth_page, base_url):
        """Clicking '上传' navigates to /upload."""
        auth_page.goto(base_url)
        auth_page.locator("a.nav-link", has_text="上传").click()
        auth_page.wait_for_url(f"{base_url}/upload", timeout=BASE_TIMEOUT)

    def test_statistics_link_navigates(self, auth_page, base_url):
        """Clicking '统计' navigates to /statistics."""
        auth_page.goto(base_url)
        auth_page.locator("a.nav-link", has_text="统计").click()
        auth_page.wait_for_url(f"{base_url}/statistics", timeout=BASE_TIMEOUT)

    def test_youtube_link_navigates(self, auth_page, base_url):
        """Clicking 'YouTube' navigates to /youtube."""
        auth_page.goto(base_url)
        auth_page.locator("a.nav-link", has_text="YouTube").click()
        auth_page.wait_for_url(f"{base_url}/youtube", timeout=BASE_TIMEOUT)

    def test_active_link_highlighted(self, auth_page, base_url):
        """Current route's nav link has the 'active' class."""
        auth_page.goto(f"{base_url}/upload")
        upload_link = auth_page.locator("a.nav-link", has_text="上传")
        upload_link.wait_for(timeout=BASE_TIMEOUT)
        assert "active" in (upload_link.get_attribute("class") or "")


class TestAuthNavigation:
    """Auth-related navigation behaviour."""

    def test_unauthenticated_sees_login_link(self, page, base_url):
        """User without token sees '登录' link in header."""
        page.goto(f"{base_url}/login")
        login_link = page.locator(".header-auth a", has_text="登录")
        login_link.wait_for(timeout=BASE_TIMEOUT)
        assert login_link.is_visible()

    def test_unauthenticated_redirect_to_login(self, page, base_url):
        """Navigating to protected route redirects to /login."""
        page.goto(f"{base_url}/upload")
        page.wait_for_url(f"**/login**", timeout=BASE_TIMEOUT)
        assert "/login" in page.url


class TestMobileMenu:
    """Mobile hamburger menu."""

    def test_hamburger_toggles_menu(self, mobile_page, base_url):
        """Tapping the hamburger button toggles the nav menu."""
        mobile_page.goto(f"{base_url}/login")
        hamburger = mobile_page.locator("button.mobile-menu-btn")
        hamburger.wait_for(timeout=BASE_TIMEOUT)
        assert hamburger.is_visible()
        hamburger.click()
        nav = mobile_page.locator("nav.nav-menu.open")
        nav.wait_for(timeout=BASE_TIMEOUT)
        assert nav.is_visible()
