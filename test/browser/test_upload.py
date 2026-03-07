"""Browser E2E tests for the Upload page (React SPA).

UploadPage structure (from UploadPage.tsx):
- div.card
  - div.card-header "上传视频"
  - DropZone component:
    - div.upload-section
      - div.upload-zone (drag & drop area, clickable)
        - div.upload-zone-icon "📁"
        - div.upload-zone-text (file name or placeholder)
      - hidden input[type=file][accept="video/*,audio/*"]
      - button.btn-primary "开始上传" (shown after file selected)
      - progress bar (shown during upload)
  - After upload: EditForm with title/description/visibility inputs
"""

import pytest

pytestmark = pytest.mark.browser

BASE_TIMEOUT = 5_000


class TestUploadPageLoad:
    """Upload page renders correctly."""

    def test_card_header_visible(self, auth_page, base_url):
        """Upload page shows '上传视频' card header."""
        auth_page.goto(f"{base_url}/upload")
        header = auth_page.locator(".card-header")
        header.wait_for(timeout=BASE_TIMEOUT)
        assert header.inner_text() == "上传视频"

    def test_drop_zone_visible(self, auth_page, base_url):
        """Upload drop zone area is visible."""
        auth_page.goto(f"{base_url}/upload")
        zone = auth_page.locator("div.upload-zone")
        zone.wait_for(timeout=BASE_TIMEOUT)
        assert zone.is_visible()

    def test_drop_zone_placeholder_text(self, auth_page, base_url):
        """Drop zone shows placeholder text when no file selected."""
        auth_page.goto(f"{base_url}/upload")
        text = auth_page.locator("div.upload-zone-text")
        text.wait_for(timeout=BASE_TIMEOUT)
        assert "拖拽文件到此处" in text.inner_text()

    def test_file_input_hidden(self, auth_page, base_url):
        """Hidden file input accepts video and audio files."""
        auth_page.goto(f"{base_url}/upload")
        file_input = auth_page.locator("input[type='file']")
        assert file_input.get_attribute("accept") == "video/*,audio/*"
        assert not file_input.is_visible()


class TestUploadAuth:
    """Auth protection on upload page."""

    def test_requires_auth(self, page, base_url):
        """Unauthenticated users are redirected to /login."""
        page.goto(f"{base_url}/upload")
        page.wait_for_url("**/login**", timeout=BASE_TIMEOUT)
        assert "/login" in page.url
