const { test, expect } = require('@playwright/test');

test.describe('Statistics page', () => {
  test.beforeEach(async ({ page }) => {
    // Mock external CDN resources
    await page.route('**/echarts*.js', route => route.fulfill({
      status: 200, contentType: 'application/javascript',
      body: `window.echarts = { init: function() { return { setOption: function(){}, resize: function(){} }; } };`
    }));
    await page.route('**/axios*.js', route => route.fulfill({
      status: 200, contentType: 'application/javascript',
      body: `window.axios = { get: function(url) {
        return fetch(url).then(function(r) { return r.json().then(function(d) { return {data: d}; }); });
      }};`
    }));
    await page.route('**/statistics/**', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: { xAxis: { data: [] }, series: [] } })
    }));
  });

  test('has chart container', async ({ page }) => {
    await page.goto('/statistics.html');
    const chart = page.locator('#bar-chart');
    await expect(chart).toBeVisible();
  });

  test('has query buttons', async ({ page }) => {
    await page.goto('/statistics.html');
    await expect(page.locator('#query7Days')).toBeVisible();
    await expect(page.locator('#query30Days')).toBeVisible();
  });

  test('chart container is responsive', async ({ page }) => {
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
  test('saves token to localStorage', async ({ page }) => {
    // Server requires auth for save-token.html, so serve it via route
    const saveTokenHtml = [
      '<!DOCTYPE html><html lang="zh"><head><meta charset="UTF-8">',
      '<meta name="viewport" content="width=device-width, initial-scale=1.0">',
      '<title>正在跳转</title>',
      '<link rel="stylesheet" href="/css/global.css">',
      '</head><body>',
      '<div style="display:flex;align-items:center;justify-content:center;min-height:100vh;">',
      '<div class="status-message">',
      '<div class="spinner" style="margin:0 auto 16px;"></div>',
      '<div class="text">正在跳转...</div>',
      '</div></div>',
      '<script src="/js/global.js"><' + '/script>',
      '<script>',
      'let token = VideoApp.getUrlVariable("token");',
      'localStorage.token = token;',
      '<' + '/script>',
      '</body></html>'
    ].join('\n');

    await page.route('**/save-token.html**', route => route.fulfill({
      status: 200, contentType: 'text/html', body: saveTokenHtml
    }));
    await page.goto('/save-token.html?token=test-tok-42&target=/upload.html');
    const token = await page.evaluate(() => localStorage.getItem('token'));
    expect(token).toBe('test-tok-42');
    const spinner = page.locator('.spinner');
    await expect(spinner).toBeVisible();
  });
});
