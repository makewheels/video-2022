"""Browser E2E tests for the Login page (React SPA)."""

import pytest

pytestmark = pytest.mark.browser

BASE_TIMEOUT = 10_000


class TestLoginPageLoad:
    """Login page renders correctly."""

    def test_login_page_loads(self, page, base_url):
        """Navigate to /login and verify the login card is visible."""
        page.goto(f"{base_url}/login")
        card = page.locator(".card")
        card.wait_for(timeout=BASE_TIMEOUT)
        assert card.locator(".card-header").inner_text() == "登录"

    def test_phone_input_present(self, page, base_url):
        """Login page has a phone number input."""
        page.goto(f"{base_url}/login")
        phone_input = page.locator("input[type='tel'][placeholder='手机号']")
        phone_input.wait_for(timeout=BASE_TIMEOUT)
        assert phone_input.is_visible()

    def test_verification_code_input_present(self, page, base_url):
        """Login page has a verification code input."""
        page.goto(f"{base_url}/login")
        code_input = page.locator("input[type='text'][placeholder='验证码']")
        code_input.wait_for(timeout=BASE_TIMEOUT)
        assert code_input.is_visible()

    def test_request_code_button_present(self, page, base_url):
        """Login page has a '获取验证码' button."""
        page.goto(f"{base_url}/login")
        btn = page.locator("button.btn-secondary", has_text="获取验证码")
        btn.wait_for(timeout=BASE_TIMEOUT)
        assert btn.is_visible()

    def test_login_button_disabled_initially(self, page, base_url):
        """Login button is disabled before requesting a code."""
        page.goto(f"{base_url}/login")
        login_btn = page.locator("button.btn-primary", has_text="登录")
        login_btn.wait_for(timeout=BASE_TIMEOUT)
        assert login_btn.is_disabled()


class TestLoginInteraction:
    """Interactive login flow tests."""

    def test_phone_input_accepts_input(self, page, base_url):
        """Type into the phone input and verify value."""
        page.goto(f"{base_url}/login")
        phone_input = page.locator("input[type='tel']")
        phone_input.wait_for(timeout=BASE_TIMEOUT)
        phone_input.fill("19900001111")
        assert phone_input.input_value() == "19900001111"

    def test_phone_input_max_length(self, page, base_url):
        """Phone input enforces maxLength=11."""
        page.goto(f"{base_url}/login")
        phone_input = page.locator("input[type='tel']")
        phone_input.wait_for(timeout=BASE_TIMEOUT)
        assert phone_input.get_attribute("maxlength") == "11"

    def test_invalid_phone_shows_toast(self, page, base_url):
        """Clicking '获取验证码' with invalid phone shows error toast."""
        page.goto(f"{base_url}/login")
        phone_input = page.locator("input[type='tel']")
        phone_input.wait_for(timeout=BASE_TIMEOUT)
        phone_input.fill("123")
        page.locator("button.btn-secondary", has_text="获取验证码").click()
        toast = page.locator(".toast.toast-error")
        toast.wait_for(timeout=BASE_TIMEOUT)
        assert "手机号" in toast.inner_text()


class TestLoginTheme:
    """Theme toggle on login page."""

    def test_theme_toggle_exists_on_login(self, page, base_url):
        """Theme toggle button is visible on the login page."""
        page.goto(f"{base_url}/login")
        theme_btn = page.locator("button.btn-icon[title='切换主题']")
        theme_btn.wait_for(timeout=BASE_TIMEOUT)
        assert theme_btn.is_visible()
