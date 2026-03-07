const { test, expect } = require('@playwright/test');

test.describe('Login page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login.html');
  });

  test('has page title', async ({ page }) => {
    await expect(page).toHaveTitle(/登录/);
  });

  test('has centered login card', async ({ page }) => {
    const card = page.locator('.card');
    await expect(card).toBeVisible();
    const box = await card.boundingBox();
    const viewport = page.viewportSize();
    const cardCenter = box.x + box.width / 2;
    const viewportCenter = viewport.width / 2;
    expect(Math.abs(cardCenter - viewportCenter)).toBeLessThan(100);
  });

  test('phone input has proper styling', async ({ page }) => {
    const input = page.locator('#input_phone');
    await expect(input).toBeVisible();
    await expect(input).toHaveAttribute('placeholder', /手机号/);
  });

  test('verification code input exists', async ({ page }) => {
    const input = page.locator('#input_verificationCode');
    await expect(input).toBeVisible();
  });

  test('login button exists and is styled', async ({ page }) => {
    const btn = page.locator('#btn_submitVerificationCode');
    await expect(btn).toBeVisible();
  });

  test('error message area exists but hidden initially', async ({ page }) => {
    const error = page.locator('#errorMessage');
    await expect(error).toBeEmpty();
  });
});

test.describe('Login page responsive', () => {
  test('card is full width on mobile', async ({ page, isMobile }) => {
    test.skip(!isMobile, 'Mobile only test');
    await page.goto('/login.html');
    const card = page.locator('.card');
    const box = await card.boundingBox();
    const viewport = page.viewportSize();
    // On small screens (<500px), card should be nearly full width
    // On tablets, card uses max-width so this test only applies to phones
    if (viewport.width < 500) {
      expect(box.width / viewport.width).toBeGreaterThan(0.85);
    }
  });
});
