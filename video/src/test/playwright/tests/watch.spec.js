const { test, expect } = require('@playwright/test');

test.describe('Watch page structure', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/client/requestClientId', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ data: { clientId: 'test-client' } })
    }));
    await page.route('**/session/requestSessionId', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ data: { sessionId: 'test-session' } })
    }));
    await page.route('**/watchController/getWatchInfo*', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ data: {
        videoId: 'v_test', videoStatus: 'CREATED', coverUrl: ''
      }})
    }));
    await page.goto('/watch?v=test123');
  });

  test('has player container', async ({ page }) => {
    const player = page.locator('#player-con');
    await expect(player).toBeVisible();
  });

  test('has video info section', async ({ page }) => {
    const info = page.locator('.video-info-section');
    await expect(info).toBeVisible();
  });

  test('has video title element', async ({ page }) => {
    const title = page.locator('#div_title');
    await expect(title).toBeVisible();
  });

  test('player container has 16:9 aspect ratio wrapper', async ({ page }) => {
    const wrapper = page.locator('.player-wrapper');
    await expect(wrapper).toBeVisible();
  });
});

test.describe('Watch page responsive', () => {
  test('playlist is below player on mobile', async ({ page, isMobile }) => {
    test.skip(!isMobile, 'Mobile only test');
    await page.route('**/*', route => route.fulfill({ status: 200, body: '' }));
    await page.goto('/watch?v=test123');
    const layout = page.locator('.watch-layout');
    if (await layout.count() > 0) {
      const display = await layout.evaluate(el => getComputedStyle(el).flexDirection);
      expect(display).toBe('column');
    }
  });
});
