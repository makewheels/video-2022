const { test, expect } = require('@playwright/test');

test.describe('Upload page', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('token', 'test-token'));
    await page.route('**/user/getUserByToken*', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: { id: 'test' } })
    }));
    await page.route('**/playlist/getMyPlaylistByPage*', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: [] })
    }));
    await page.goto('/upload.html');
  });

  test('has page title', async ({ page }) => {
    await expect(page).toHaveTitle(/上传/);
  });

  test('has upload zone', async ({ page }) => {
    const zone = page.locator('.upload-zone');
    await expect(zone).toBeVisible();
  });

  test('has title input', async ({ page }) => {
    const input = page.locator('#input_title');
    await expect(input).toBeVisible();
  });

  test('has progress bar', async ({ page }) => {
    const bar = page.locator('#progress-bar');
    await expect(bar).toBeVisible();
  });

  test('has playlist selector', async ({ page }) => {
    const select = page.locator('#select_playlist');
    await expect(select).toBeVisible();
  });
});
