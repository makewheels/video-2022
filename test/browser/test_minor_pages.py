"""Browser E2E tests for Statistics and YouTube pages (React SPA).

StatisticsPage structure (from StatisticsPage.tsx):
- div.page-container
  - div.card
    - div.card-header "数据统计"
    - div.dashboard-controls with preset buttons and date inputs
    - div.chart-container with ReactECharts

YouTubePage structure (from YouTubePage.tsx):
- div.card (centered)
  - div.card-header "搬运YouTube视频"
  - input.form-input[type=url][placeholder="YouTube视频链接"]
  - button.btn-primary "提交"
  - result links (after submit)
"""

import pytest

pytestmark = pytest.mark.browser

BASE_TIMEOUT = 5_000


class TestStatisticsPage:
    """Statistics page tests."""

    def test_card_header_visible(self, auth_page, base_url):
        """Statistics page shows '数据统计' card header."""
        auth_page.goto(f"{base_url}/statistics")
        header = auth_page.locator(".card-header")
        header.wait_for(timeout=BASE_TIMEOUT)
        assert header.inner_text() == "数据统计"

    def test_dashboard_controls_visible(self, auth_page, base_url):
        """Dashboard preset buttons are visible."""
        auth_page.goto(f"{base_url}/statistics")
        controls = auth_page.locator(".dashboard-controls")
        controls.wait_for(timeout=BASE_TIMEOUT)
        assert controls.is_visible()

    def test_preset_buttons_present(self, auth_page, base_url):
        """'最近7天' and '最近30天' preset buttons exist."""
        auth_page.goto(f"{base_url}/statistics")
        btn7 = auth_page.locator("button", has_text="最近7天")
        btn30 = auth_page.locator("button", has_text="最近30天")
        btn7.wait_for(timeout=BASE_TIMEOUT)
        assert btn7.is_visible()
        assert btn30.is_visible()

    def test_date_inputs_present(self, auth_page, base_url):
        """Start and end date inputs are visible."""
        auth_page.goto(f"{base_url}/statistics")
        date_inputs = auth_page.locator("input[type='date']")
        date_inputs.first.wait_for(timeout=BASE_TIMEOUT)
        assert date_inputs.count() == 2

    def test_chart_container_present(self, auth_page, base_url):
        """Chart container is visible."""
        auth_page.goto(f"{base_url}/statistics")
        chart = auth_page.locator(".chart-container")
        chart.wait_for(timeout=BASE_TIMEOUT)
        assert chart.is_visible()

    def test_requires_auth(self, page, base_url):
        """Unauthenticated users are redirected to /login."""
        page.goto(f"{base_url}/statistics")
        page.wait_for_url("**/login**", timeout=BASE_TIMEOUT)
        assert "/login" in page.url


class TestYouTubePage:
    """YouTube import page tests."""

    def test_card_header_visible(self, auth_page, base_url):
        """YouTube page shows '搬运YouTube视频' card header."""
        auth_page.goto(f"{base_url}/youtube")
        header = auth_page.locator(".card-header")
        header.wait_for(timeout=BASE_TIMEOUT)
        assert header.inner_text() == "搬运YouTube视频"

    def test_url_input_present(self, auth_page, base_url):
        """YouTube URL input is visible."""
        auth_page.goto(f"{base_url}/youtube")
        url_input = auth_page.locator(
            "input.form-input[type='url'][placeholder='YouTube视频链接']"
        )
        url_input.wait_for(timeout=BASE_TIMEOUT)
        assert url_input.is_visible()

    def test_submit_button_present(self, auth_page, base_url):
        """Submit button with text '提交' is visible."""
        auth_page.goto(f"{base_url}/youtube")
        btn = auth_page.locator("button.btn-primary", has_text="提交")
        btn.wait_for(timeout=BASE_TIMEOUT)
        assert btn.is_visible()

    def test_url_input_accepts_text(self, auth_page, base_url):
        """URL input accepts a YouTube link."""
        auth_page.goto(f"{base_url}/youtube")
        url_input = auth_page.locator("input.form-input[type='url']")
        url_input.wait_for(timeout=BASE_TIMEOUT)
        url_input.fill("https://www.youtube.com/watch?v=test123")
        assert url_input.input_value() == "https://www.youtube.com/watch?v=test123"

    def test_requires_auth(self, page, base_url):
        """Unauthenticated users are redirected to /login."""
        page.goto(f"{base_url}/youtube")
        page.wait_for_url("**/login**", timeout=BASE_TIMEOUT)
        assert "/login" in page.url
