const { test, expect } = require('@playwright/test');

test.describe('Global CSS', () => {
  test('global.css is loaded on login page', async ({ page }) => {
    await page.goto('/login.html');
    const link = page.locator('link[href*="global.css"]');
    await expect(link).toHaveCount(1);
  });

  test('global.css is loaded on upload page', async ({ page }) => {
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
    const link = page.locator('link[href*="global.css"]');
    await expect(link).toHaveCount(1);
  });

  test('body has css variable --bg-primary defined', async ({ page }) => {
    await page.goto('/login.html');
    const bgColor = await page.evaluate(() =>
      getComputedStyle(document.body).getPropertyValue('--bg-primary').trim()
    );
    expect(bgColor).toBeTruthy();
  });
});

test.describe('Theme toggle', () => {
  test('theme toggle button exists on login page', async ({ page }) => {
    await page.goto('/login.html');
    const toggle = page.locator('#theme-toggle');
    await expect(toggle).toBeVisible();
  });

  test('clicking toggle switches to dark mode', async ({ page }) => {
    await page.goto('/login.html');
    await page.click('#theme-toggle');
    const theme = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
    expect(theme).toBe('dark');
  });

  test('theme preference persists in localStorage', async ({ page }) => {
    await page.goto('/login.html');
    await page.click('#theme-toggle');
    const stored = await page.evaluate(() => localStorage.getItem('theme'));
    expect(stored).toBeTruthy();
  });
});

test.describe('Responsive layout', () => {
  test('viewport meta tag exists', async ({ page }) => {
    await page.goto('/login.html');
    const meta = page.locator('meta[name="viewport"]');
    await expect(meta).toHaveCount(1);
  });
});
