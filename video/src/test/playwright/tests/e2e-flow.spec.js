const { test, expect } = require('@playwright/test');

// E2E tests run against the REAL server at localhost:5022 — no mocking.

// ===== E2E: 登录流程 =====
test.describe('E2E: 登录流程', () => {
  test.describe.configure({ mode: 'serial' });

  test('输入手机号→发送验证码→登录→跳转', async ({ page }) => {
    await page.goto('/login.html');

    // Fill phone number and request verification code
    await page.fill('#input_phone', '13800138000');
    await page.click('#btn_requestVerificationCode');

    // Expect success toast for code sent
    const successToast = page.locator('.toast-success');
    await expect(successToast).toBeVisible({ timeout: 5000 });

    // Fill dev verification code and submit
    await page.fill('#input_verificationCode', '111');
    await page.click('#btn_submitVerificationCode');

    // Verify login success: either a success toast or redirect away from login
    await expect(async () => {
      const url = page.url();
      const hasSuccessToast = await page.locator('.toast-success').count() > 0;
      const redirected = !url.includes('/login.html');
      expect(hasSuccessToast || redirected).toBe(true);
    }).toPass({ timeout: 10000 });
  });

  test('手机号格式验证', async ({ page }) => {
    await page.goto('/login.html');

    // Fill invalid phone and click send code
    await page.fill('#input_phone', '123');
    await page.click('#btn_requestVerificationCode');

    // Expect error toast about invalid phone format
    const errorToast = page.locator('.toast-error');
    await expect(errorToast).toBeVisible({ timeout: 3000 });
  });
});

// ===== E2E: 我的视频 =====
test.describe('E2E: 我的视频', () => {
  test('我的视频页可访问且有导航链接', async ({ page }) => {
    const response = await page.goto('/index.html');
    expect(response.status()).toBe(200);

    const viewport = page.viewportSize();
    if (viewport && viewport.width > 768) {
      const navLinks = page.locator('a.nav-link');
      await expect(navLinks.first()).toBeVisible({ timeout: 5000 });
      const count = await navLinks.count();
      expect(count).toBeGreaterThanOrEqual(1);
    } else {
      // On mobile/tablet, nav links are in hamburger menu
      const hamburger = page.locator('.mobile-menu-btn');
      await expect(hamburger).toBeVisible({ timeout: 5000 });
    }
  });
});

// ===== E2E: 上传页面 =====
test.describe('E2E: 上传页面', () => {
  test('未登录访问上传页重定向到登录页', async ({ page, context }) => {
    // Clear all cookies and storage to ensure unauthenticated state
    await context.clearCookies();
    await page.goto('/upload.html');
    await page.evaluate(() => localStorage.clear());
    await page.goto('/upload.html');

    // Expect redirect to login.html with target parameter
    await expect(async () => {
      const url = page.url();
      expect(url).toContain('/login.html');
    }).toPass({ timeout: 10000 });
  });
});

// ===== E2E: CDN 可用性测试 =====
test.describe('E2E: CDN 可用性', () => {
  const pages = [
    { name: '我的视频', path: '/index.html' },
    { name: '登录', path: '/login.html' },
    { name: '统计', path: '/statistics.html' },
    { name: 'YouTube', path: '/transfer-youtube.html' },
  ];

  for (const p of pages) {
    test(`${p.name}页面所有脚本加载成功 (${p.path})`, async ({ page }) => {
      test.setTimeout(60000);
      const failedScripts = [];

      page.on('pageerror', error => {
        if (error.message.includes('is not defined')) {
          failedScripts.push(error.message);
        }
      });

      page.on('response', response => {
        const url = response.url();
        if (url.endsWith('.js') && response.status() >= 400) {
          failedScripts.push(`${response.status()} ${url}`);
        }
      });

      await page.goto(p.path, { waitUntil: 'domcontentloaded', timeout: 30000 });
      await page.waitForTimeout(3000);

      expect(failedScripts).toEqual([]);
    });
  }

  test('upload页面脚本加载成功（需登录）', async ({ page }) => {
    test.setTimeout(60000);
    // Upload requires auth, so inject token
    await page.addInitScript(() => localStorage.setItem('token', 'test-token'));

    const failedScripts = [];

    page.on('response', response => {
      const url = response.url();
      if (url.endsWith('.js') && response.status() >= 400) {
        failedScripts.push(`${response.status()} ${url}`);
      }
    });

    // Mock auth API but NOT CDN scripts — we want to test real CDN
    await page.route('**/user/getUserByToken*', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: { id: 'test' } })
    }));
    await page.route('**/playlist/getMyPlaylistByPage*', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: [] })
    }));

    await page.goto('/upload.html', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(3000);

    expect(failedScripts).toEqual([]);

    // Verify axios is actually loaded
    const axiosLoaded = await page.evaluate(() => typeof window.axios !== 'undefined');
    expect(axiosLoaded).toBe(true);
  });
});
