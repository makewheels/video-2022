const { test, expect } = require('@playwright/test');

test.describe('Statistics page', () => {
  test('has chart container', async ({ page }) => {
    await page.route('**/statistics/*', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: {} })
    }));
    await page.goto('/statistics.html');
    const chart = page.locator('#bar-chart');
    await expect(chart).toBeVisible();
  });

  test('has query buttons', async ({ page }) => {
    await page.route('**/statistics/*', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: {} })
    }));
    await page.goto('/statistics.html');
    await expect(page.locator('#query7Days')).toBeVisible();
    await expect(page.locator('#query30Days')).toBeVisible();
  });

  test('chart container is responsive', async ({ page }) => {
    await page.route('**/statistics/*', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: {} })
    }));
    await page.goto('/statistics.html');
    const chart = page.locator('#bar-chart');
    const box = await chart.boundingBox();
    const viewport = page.viewportSize();
    expect(box.width).toBeLessThanOrEqual(viewport.width);
  });
});

test.describe('Transfer YouTube page', () => {
  test('has URL input', async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('token', 'test-token'));
    await page.goto('/transfer-youtube.html');
    const input = page.locator('#input_youtubeUrl');
    await expect(input).toBeVisible();
  });

  test('has submit button', async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('token', 'test-token'));
    await page.goto('/transfer-youtube.html');
    await expect(page.locator('#btn_submit')).toBeVisible();
  });
});

test.describe('Save token page', () => {
  test('has redirect message', async ({ page }) => {
    await page.goto('/save-token.html?token=test&target=/upload.html');
    const body = await page.textContent('body');
    expect(body).toContain('跳转');
  });
});
