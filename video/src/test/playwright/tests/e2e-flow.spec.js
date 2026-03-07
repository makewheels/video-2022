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

// ===== E2E: 首页 =====
test.describe('E2E: 首页', () => {
  test('首页可访问且有导航链接', async ({ page }) => {
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
