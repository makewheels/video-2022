"""Browser E2E tests for the Settings page (React SPA).

SettingsPage structure:
- div.page-container
  - div.card (max-width: 600px)
    - div.card-header "个人设置"
    - avatar display (img or placeholder)
    - label "昵称" + input.form-input
    - label "个人简介" + textarea.form-input
    - button.btn-primary "保存"
"""

import pytest

pytestmark = pytest.mark.browser

BASE_TIMEOUT = 10_000


class TestSettingsPageLoad:
    """Settings page renders correctly."""

    def test_card_header_visible(self, auth_page, base_url):
        """Settings page shows '个人设置' card header."""
        auth_page.goto(f"{base_url}/settings")
        header = auth_page.locator(".card-header")
        header.wait_for(timeout=BASE_TIMEOUT)
        assert header.inner_text() == "个人设置"

    def test_nickname_input_present(self, auth_page, base_url):
        """Nickname input is present."""
        auth_page.goto(f"{base_url}/settings")
        label = auth_page.locator("label.form-label", has_text="昵称")
        label.wait_for(timeout=BASE_TIMEOUT)
        assert label.is_visible()

    def test_bio_textarea_present(self, auth_page, base_url):
        """Bio textarea is present."""
        auth_page.goto(f"{base_url}/settings")
        label = auth_page.locator("label.form-label", has_text="个人简介")
        label.wait_for(timeout=BASE_TIMEOUT)
        assert label.is_visible()

    def test_save_button_present(self, auth_page, base_url):
        """Save button is present."""
        auth_page.goto(f"{base_url}/settings")
        btn = auth_page.locator("button.btn-primary", has_text="保存")
        btn.wait_for(timeout=BASE_TIMEOUT)
        assert btn.is_visible()


class TestSettingsAuth:
    """Auth protection on settings page."""

    def test_requires_auth(self, page, base_url):
        """Unauthenticated users are redirected to /login."""
        page.goto(f"{base_url}/settings")
        page.wait_for_function(
            "() => window.location.pathname.includes('/login')",
            timeout=BASE_TIMEOUT,
        )
        assert "/login" in page.url


class TestSettingsFormValidation:
    """Settings表单交互验证."""

    def test_bio_character_count_visible(self, auth_page, base_url):
        """简介字符计数器显示."""
        auth_page.goto(f"{base_url}/settings")
        counter = auth_page.locator("text=/\\d+\\/200/")
        counter.wait_for(timeout=BASE_TIMEOUT)
        assert counter.is_visible()

    def test_nickname_accepts_input(self, auth_page, base_url):
        """昵称输入框可输入文字."""
        auth_page.goto(f"{base_url}/settings")
        nickname_input = auth_page.locator("input[placeholder='输入昵称']")
        nickname_input.wait_for(timeout=BASE_TIMEOUT)
        nickname_input.fill("测试昵称")
        assert nickname_input.input_value() == "测试昵称"

    def test_bio_accepts_input(self, auth_page, base_url):
        """简介文本域可输入文字."""
        auth_page.goto(f"{base_url}/settings")
        bio_textarea = auth_page.locator("textarea")
        bio_textarea.wait_for(timeout=BASE_TIMEOUT)
        bio_textarea.fill("测试简介")
        assert bio_textarea.input_value() == "测试简介"
