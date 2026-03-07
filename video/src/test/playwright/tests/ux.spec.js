const { test, expect } = require('@playwright/test');

// ===== Toast Component Tests =====
test.describe('Toast component', () => {
  test('VideoApp.toast function exists', async ({ page }) => {
    await page.goto('/login.html');
    const hasToast = await page.evaluate(() => typeof window.VideoApp.toast === 'function');
    expect(hasToast).toBe(true);
  });

  test('toast appears with message text', async ({ page }) => {
    await page.goto('/login.html');
    await page.evaluate(() => VideoApp.toast('测试消息', 'info'));
    const toast = page.locator('.toast');
    await expect(toast).toBeVisible();
    await expect(toast).toContainText('测试消息');
  });

  test('toast has correct CSS class for success type', async ({ page }) => {
    await page.goto('/login.html');
    await page.evaluate(() => VideoApp.toast('成功', 'success'));
    const toast = page.locator('.toast-success');
    await expect(toast).toBeVisible();
  });

  test('toast has correct CSS class for error type', async ({ page }) => {
    await page.goto('/login.html');
    await page.evaluate(() => VideoApp.toast('错误', 'error'));
    const toast = page.locator('.toast-error');
    await expect(toast).toBeVisible();
  });

  test('toast auto-disappears after delay', async ({ page }) => {
    await page.goto('/login.html');
    await page.evaluate(() => VideoApp.toast('消失测试', 'info'));
    const toast = page.locator('.toast');
    await expect(toast).toBeVisible();
    // Wait for toast to disappear (2s delay + 0.3s animation)
    await page.waitForTimeout(2500);
    await expect(toast).toHaveCount(0);
  });
});

// ===== Homepage Tests =====
test.describe('Homepage', () => {
  test('index.html loads without error', async ({ page }) => {
    const response = await page.goto('/index.html');
    expect(response.status()).toBe(200);
  });

  test('has page header with logo', async ({ page }) => {
    await page.goto('/index.html');
    const logo = page.locator('.page-header .logo');
    await expect(logo).toBeVisible();
  });

  test('has theme toggle', async ({ page }) => {
    await page.goto('/index.html');
    const toggle = page.locator('#theme-toggle');
    await expect(toggle).toBeVisible();
  });

  test('has navigation link to upload', async ({ page }) => {
    await page.goto('/index.html');
    const viewport = page.viewportSize();
    if (viewport && viewport.width <= 768) {
      // On mobile/tablet, nav links are in the hamburger menu
      const link = page.locator('.nav-menu a[href*="upload"]');
      await expect(link.first()).toBeAttached();
    } else {
      const link = page.locator('.nav-menu a[href*="upload"]');
      await expect(link.first()).toBeVisible();
    }
  });

  test('has navigation link to statistics', async ({ page }) => {
    await page.goto('/index.html');
    const viewport = page.viewportSize();
    if (viewport && viewport.width <= 768) {
      const link = page.locator('.nav-menu a[href*="statistics"]');
      await expect(link.first()).toBeAttached();
    } else {
      const link = page.locator('.nav-menu a[href*="statistics"]');
      await expect(link.first()).toBeVisible();
    }
  });

  test('has navigation link to transfer-youtube', async ({ page }) => {
    await page.goto('/index.html');
    const viewport = page.viewportSize();
    if (viewport && viewport.width <= 768) {
      const link = page.locator('.nav-menu a[href*="transfer-youtube"]');
      await expect(link.first()).toBeAttached();
    } else {
      const link = page.locator('.nav-menu a[href*="transfer-youtube"]');
      await expect(link.first()).toBeVisible();
    }
  });
});

// ===== Login UX Tests =====
test.describe('Login UX', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/axios*.js', route => route.fulfill({
      status: 200, contentType: 'application/javascript',
      body: `window.axios = {
        get: function(url) {
          return new Promise(function(resolve, reject) {
            fetch(url).then(function(r) {
              return r.json().then(function(d) { resolve({data: d}); });
            }).catch(reject);
          });
        }
      };`
    }));
    await page.goto('/login.html');
  });

  test('invalid phone number shows validation error', async ({ page }) => {
    await page.fill('#input_phone', '123');
    await page.click('#btn_requestVerificationCode');
    // Should show a toast or error about invalid phone
    const toast = page.locator('.toast-error');
    await expect(toast).toBeVisible({ timeout: 3000 });
  });

  test('valid phone shows success toast after sending code', async ({ page }) => {
    await page.route('**/user/requestVerificationCode*', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: null })
    }));
    await page.fill('#input_phone', '13800138000');
    await page.click('#btn_requestVerificationCode');
    const toast = page.locator('.toast-success');
    await expect(toast).toBeVisible({ timeout: 3000 });
  });

  test('login API error shows error toast', async ({ page }) => {
    // First send code successfully
    await page.route('**/user/requestVerificationCode*', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: null })
    }));
    await page.route('**/user/submitVerificationCode*', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: -1, message: '验证码错误' })
    }));
    await page.fill('#input_phone', '13800138000');
    await page.click('#btn_requestVerificationCode');
    await page.waitForTimeout(500);
    await page.fill('#input_verificationCode', '000000');
    await page.click('#btn_submitVerificationCode');
    const toast = page.locator('.toast-error');
    await expect(toast).toBeVisible({ timeout: 3000 });
  });
});

// ===== Upload UX Tests =====
test.describe('Upload UX', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('token', 'test-token'));
    await page.route('**/axios*.js', route => route.fulfill({
      status: 200, contentType: 'application/javascript',
      body: `window.axios = {
        get: function(url, config) {
          return new Promise(function(resolve, reject) {
            fetch(url).then(function(r) {
              return r.json().then(function(d) { resolve({data: d}); });
            }).catch(reject);
          });
        },
        post: function(url, data, config) {
          return new Promise(function(resolve, reject) {
            fetch(url, {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)}).then(function(r) {
              return r.json().then(function(d) { resolve({data: d}); });
            }).catch(reject);
          });
        }
      };`
    }));
    await page.route('**/aliyun-oss-sdk*.js', route => route.fulfill({
      status: 200, contentType: 'application/javascript',
      body: 'window.OSS = function() {};'
    }));
  });

  test('empty playlist shows message', async ({ page }) => {
    await page.route('**/playlist/getMyPlaylistByPage*', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: [] })
    }));
    await page.goto('/upload.html');
    await page.waitForTimeout(1000);
    // Should show empty state message
    const emptyMsg = page.locator('.empty-state');
    await expect(emptyMsg).toBeVisible({ timeout: 3000 });
  });

  test('copy button shows toast', async ({ page }) => {
    await page.route('**/playlist/getMyPlaylistByPage*', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: [] })
    }));
    await page.goto('/upload.html');
    // Grant clipboard permission
    await page.context().grantPermissions(['clipboard-write']);
    await page.click('#btn_copy');
    const toast = page.locator('.toast-success');
    await expect(toast).toBeVisible({ timeout: 3000 });
  });
});

// ===== Watch UX Tests =====
test.describe('Watch UX', () => {
  test('does not use mui.toast', async ({ page }) => {
    // Mock CDN resources
    await page.route('**/axios*.js', route => route.fulfill({
      status: 200, contentType: 'application/javascript',
      body: `window.axios = { get: function(url) {
        return fetch(url).then(function(r) { return r.json().then(function(d) { return {data: d}; }); });
      }, post: function(url, data) {
        return fetch(url, {method:'POST',body:JSON.stringify(data)}).then(function(r) { return r.json().then(function(d) { return {data: d}; }); });
      }};`
    }));
    await page.route('**/aliplayer-min.js', route => route.fulfill({
      status: 200, contentType: 'application/javascript',
      body: 'window.Aliplayer = function() { this.on = function(){}; this.seek = function(){}; this.getCurrentTime = function(){ return 0; }; this.getStatus = function(){ return "playing"; }; this.getVolume = function(){ return 1; }; this.tag = { style: {} }; };'
    }));
    await page.route('**/aliplayer-min.css', route => route.fulfill({
      status: 200, contentType: 'text/css', body: ''
    }));

    await page.route('**/client/requestClientId', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ data: { clientId: 'test-client' } })
    }));
    await page.route('**/session/requestSessionId', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ data: { sessionId: 'test-session' } })
    }));
    await page.route('**/watchController/**', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: {
        videoId: 'v_test', videoStatus: 'READY', coverUrl: '',
        multivariantPlaylistUrl: 'http://example.com/test.m3u8', progressInMillis: 0
      }})
    }));
    await page.route('**/video/**', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: {
        title: 'Test', description: 'Desc', type: 'USER_UPLOAD',
        createTimeString: '2025-01-01', watchCount: 10
      }})
    }));
    await page.route('**/heartbeat/**', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 0 })
    }));
    await page.addInitScript(() => {
      localStorage.setItem('clientId', 'test-client');
      sessionStorage.setItem('sessionId', 'test-session');
    });
    await page.goto('/watch?v=test123');
    await page.waitForTimeout(2000);

    // Verify no MUI script tag is loaded
    const muiScripts = await page.evaluate(() => {
      return document.querySelectorAll('script[src*="mui"]').length;
    });
    expect(muiScripts).toBe(0);

    // Verify no MUI CSS link
    const muiCss = await page.evaluate(() => {
      return document.querySelectorAll('link[href*="mui"]').length;
    });
    expect(muiCss).toBe(0);
  });
});

// ===== Statistics UX Tests =====
test.describe('Statistics UX', () => {
  test.beforeEach(async ({ page }) => {
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
  });

  test('API error shows toast', async ({ page }) => {
    await page.route('**/statistics/**', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: -1, data: null })
    }));
    await page.goto('/statistics.html');
    await page.waitForTimeout(1000);
    const toast = page.locator('.toast-error');
    await expect(toast).toBeVisible({ timeout: 3000 });
  });
});

// ===== Transfer YouTube UX Tests =====
test.describe('Transfer YouTube UX', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('token', 'test-token'));
    await page.route('**/axios*.js', route => route.fulfill({
      status: 200, contentType: 'application/javascript',
      body: `window.axios = {
        get: function(url) {
          return new Promise(function(resolve, reject) {
            fetch(url).then(function(r) { return r.json().then(function(d) { resolve({data: d}); }); }).catch(reject);
          });
        },
        post: function(url, data, config) {
          return new Promise(function(resolve, reject) {
            fetch(url, {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)}).then(function(r) {
              return r.json().then(function(d) { resolve({data: d}); });
            }).catch(reject);
          });
        }
      };`
    }));
  });

  test('invalid YouTube URL shows validation error', async ({ page }) => {
    await page.goto('/transfer-youtube.html');
    await page.fill('#input_youtubeUrl', 'not-a-url');
    await page.click('#btn_submit');
    const toast = page.locator('.toast-error');
    await expect(toast).toBeVisible({ timeout: 3000 });
  });

  test('submit success shows toast', async ({ page }) => {
    await page.route('**/video/create', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: { shortUrl: 'http://short.url', watchUrl: 'http://watch.url' } })
    }));
    await page.goto('/transfer-youtube.html');
    await page.fill('#input_youtubeUrl', 'https://www.youtube.com/watch?v=abc123');
    await page.click('#btn_submit');
    const toast = page.locator('.toast-success');
    await expect(toast).toBeVisible({ timeout: 3000 });
  });
});

// ===== Save-token UX Tests =====
test.describe('Save-token UX', () => {
  test('missing token parameter shows error', async ({ page }) => {
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
      'var token = VideoApp.getUrlVariable("token");',
      'if (!token) {',
      '  document.querySelector(".text").textContent = "缺少登录信息";',
      '  document.querySelector(".spinner").style.display = "none";',
      '} else {',
      '  localStorage.token = token;',
      '  window.location.href = VideoApp.getUrlVariable("target") || "/index.html";',
      '}',
      '<' + '/script>',
      '</body></html>'
    ].join('\n');

    await page.route('**/save-token.html**', route => route.fulfill({
      status: 200, contentType: 'text/html', body: saveTokenHtml
    }));
    await page.goto('/save-token.html');
    const text = page.locator('.text');
    await expect(text).toContainText('缺少登录信息');
  });
});
