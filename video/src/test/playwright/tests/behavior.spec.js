const { test, expect } = require('@playwright/test');

// Behavioral tests: real user-flow interactions, not DOM existence checks.
// Only runs on desktop project for simplicity and speed.

// ===== Login Flow Interaction =====
test.describe('Login flow behavior', () => {
  test.describe.configure({ mode: 'serial' });

  test('countdown starts after sending verification code', async ({ page }) => {
    await page.goto('/login.html');

    const codeBtn = page.locator('#btn_requestVerificationCode');
    await expect(codeBtn).toHaveText('获取验证码');
    await expect(codeBtn).toBeEnabled();

    await page.fill('#input_phone', '19900009999');
    await page.click('#btn_requestVerificationCode');

    // After successful send, button should show countdown text
    await expect(codeBtn).toContainText('秒后重试', { timeout: 5000 });
    await expect(codeBtn).toBeDisabled();
  });

  test('login button enables after code is sent', async ({ page }) => {
    await page.goto('/login.html');

    const loginBtn = page.locator('#btn_submitVerificationCode');
    // Initially disabled
    await expect(loginBtn).toBeDisabled();

    await page.fill('#input_phone', '19900009999');
    await page.click('#btn_requestVerificationCode');
    await expect(page.locator('.toast-success')).toBeVisible({ timeout: 5000 });

    // After code sent, login button should become enabled
    await expect(loginBtn).toBeEnabled({ timeout: 3000 });
  });

  test('full login → redirect flow', async ({ page }) => {
    await page.goto('/login.html');

    await page.fill('#input_phone', '19900009999');
    await page.click('#btn_requestVerificationCode');
    await expect(page.locator('.toast-success')).toBeVisible({ timeout: 5000 });

    await page.fill('#input_verificationCode', '111');
    await page.click('#btn_submitVerificationCode');

    // Should redirect away from login page (to save-token then index)
    await expect(async () => {
      expect(page.url()).not.toContain('/login.html');
    }).toPass({ timeout: 10000 });
  });
});

// ===== Desktop Navigation Behavior =====
test.describe('Desktop nav click navigation', () => {
  test('clicking nav links navigates to correct pages', async ({ page }) => {
    await page.goto('/');
    const viewport = page.viewportSize();
    test.skip(!viewport || viewport.width <= 768, 'Desktop only');

    // Click "上传" link → navigate (may redirect to login if unauthenticated)
    await page.click('.nav-menu .nav-link:has-text("上传")');
    await expect(async () => {
      const url = page.url();
      expect(url.includes('/upload.html') || url.includes('/login.html')).toBe(true);
    }).toPass({ timeout: 5000 });

    // Navigate to login page via nav link
    await page.goto('/login.html');
    await page.click('.nav-menu .nav-link:has-text("首页")');
    await page.waitForURL(/\/$|\/index\.html/, { timeout: 5000 });
  });

  test('active nav link updates on navigation', async ({ page }) => {
    await page.goto('/login.html');
    const viewport = page.viewportSize();
    test.skip(!viewport || viewport.width <= 768, 'Desktop only');

    // Login page links: home should not be active
    const homeLink = page.locator('.nav-link[href="/"]');
    await expect(homeLink).not.toHaveClass(/active/);

    // Navigate to home and check active state
    await page.goto('/');
    const homeLinkOnHome = page.locator('.nav-link[href="/"]');
    await expect(homeLinkOnHome).toHaveClass(/active/);
  });
});

// ===== Mobile Menu Behavior =====
test.describe('Mobile hamburger menu', () => {
  test('hamburger toggles menu and clicking link navigates', async ({ browser }) => {
    const context = await browser.newContext({
      viewport: { width: 375, height: 812 },
    });
    const page = await context.newPage();
    await page.goto('/');

    const hamburger = page.locator('.mobile-menu-btn');
    const navMenu = page.locator('.nav-menu');

    // Menu should not have 'open' class initially
    await expect(navMenu).not.toHaveClass(/open/);

    // Click hamburger → menu opens
    await hamburger.click();
    await expect(navMenu).toHaveClass(/open/);
    await expect(hamburger).toHaveText('✕');

    // Click a nav link → navigates and menu closes
    await page.click('.nav-menu .nav-link:has-text("首页")');
    await page.waitForURL(/\/$|\/index\.html/, { timeout: 5000 });
    expect(page.url()).toMatch(/\/$|\/index\.html/);

    await context.close();
  });

  test('hamburger toggles back to closed state', async ({ browser }) => {
    const context = await browser.newContext({
      viewport: { width: 375, height: 812 },
    });
    const page = await context.newPage();
    await page.goto('/');

    const hamburger = page.locator('.mobile-menu-btn');
    const navMenu = page.locator('.nav-menu');

    // Open menu
    await hamburger.click();
    await expect(navMenu).toHaveClass(/open/);

    // Close menu by clicking hamburger again
    await hamburger.click();
    await expect(navMenu).not.toHaveClass(/open/);
    await expect(hamburger).toHaveText('☰');

    await context.close();
  });
});

// ===== Homepage Quick Action Cards =====
test.describe('Homepage quick action behavior', () => {
  test('upload card navigates away from homepage', async ({ page }) => {
    await page.goto('/');
    const uploadCard = page.locator('.quick-action-card[href="/upload.html"]');
    await expect(uploadCard).toBeVisible();

    await uploadCard.click();
    // Without login, should redirect to login; with login, to upload
    await expect(async () => {
      const url = page.url();
      expect(url.includes('/upload.html') || url.includes('/login.html')).toBe(true);
    }).toPass({ timeout: 5000 });
  });

  test('youtube card click navigates away from homepage', async ({ page }) => {
    await page.goto('/');
    const ytCard = page.locator('.quick-action-card[href="/transfer-youtube.html"]');
    await ytCard.click();
    // May redirect to login (auth required) or transfer-youtube
    await expect(async () => {
      const url = page.url();
      expect(url.includes('/transfer-youtube.html') || url.includes('/login.html')).toBe(true);
    }).toPass({ timeout: 5000 });
  });
});

// ===== Upload Auth Redirect Behavior =====
test.describe('Upload page auth redirect', () => {
  test('unauthenticated visit redirects to login with target param', async ({ page, context }) => {
    await context.clearCookies();
    await page.goto('/login.html', { waitUntil: 'domcontentloaded' });
    await page.evaluate(() => localStorage.clear());

    await page.goto('/upload.html');
    await expect(async () => {
      const url = page.url();
      expect(url).toContain('/login.html');
      expect(url).toContain('target=');
    }).toPass({ timeout: 10000 });
  });

  test('authenticated user stays on upload page', async ({ page }) => {
    // Set token via login page first, then navigate
    await page.goto('/login.html', { waitUntil: 'domcontentloaded' });
    await page.evaluate(() => localStorage.setItem('token', 'test-token'));
    await page.goto('/upload.html', { waitUntil: 'domcontentloaded' });

    // Should remain on upload page
    await page.waitForTimeout(1000);
    expect(page.url()).toContain('/upload.html');
    await expect(page).toHaveTitle(/上传/);
  });
});

// ===== Theme Toggle Behavior =====
test.describe('Theme toggle interaction', () => {
  test('toggle switches between light and dark mode', async ({ page }) => {
    await page.goto('/');
    const toggle = page.locator('#theme-toggle');

    // First click → dark mode
    await toggle.click();
    const afterFirst = await page.evaluate(() =>
      document.documentElement.getAttribute('data-theme')
    );
    expect(afterFirst).toBe('dark');

    // Second click → light mode
    await toggle.click();
    const afterSecond = await page.evaluate(() =>
      document.documentElement.getAttribute('data-theme')
    );
    expect(afterSecond).toBe('light');
  });

  test('theme persists across page navigation', async ({ page }) => {
    await page.goto('/');
    await page.click('#theme-toggle');
    const theme = await page.evaluate(() => localStorage.getItem('theme'));

    // Navigate to another page
    await page.goto('/login.html');
    const themeOnLogin = await page.evaluate(() =>
      document.documentElement.getAttribute('data-theme')
    );
    expect(themeOnLogin).toBe(theme);
  });
});
