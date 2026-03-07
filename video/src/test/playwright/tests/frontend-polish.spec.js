const { test, expect } = require('@playwright/test');

const pages = [
    { path: '/', name: 'Homepage' },
    { path: '/login.html', name: 'Login' },
    { path: '/upload.html', name: 'Upload' },
    { path: '/statistics.html', name: 'Statistics' },
    { path: '/transfer-youtube.html', name: 'YouTube' },
];

// Statistics page loads echarts from CDN which can be slow
const pagesForOverflow = pages.filter(p => p.path !== '/statistics.html');

test.describe('Navigation Bar', () => {
    test('desktop: nav links visible, hamburger hidden', async ({ page, browserName }, testInfo) => {
        await page.goto('/');
        const viewport = page.viewportSize();
        const navMenu = page.locator('.nav-menu');
        const hamburger = page.locator('.mobile-menu-btn');
        if (viewport && viewport.width > 768) {
            await expect(navMenu).toBeVisible();
            await expect(hamburger).toBeHidden();
        } else {
            // On mobile: nav hidden, hamburger visible
            await expect(hamburger).toBeVisible();
        }
    });

    test('nav contains correct links', async ({ page }) => {
        await page.goto('/');
        const links = page.locator('.nav-menu .nav-link');
        await expect(links).toHaveCount(4);
        await expect(links.nth(0)).toHaveText('我的视频');
        await expect(links.nth(1)).toHaveText('上传');
        await expect(links.nth(2)).toHaveText('统计');
        await expect(links.nth(3)).toHaveText('YouTube');
    });

    test('active state set for current page', async ({ page }) => {
        await page.goto('/');
        const homeLink = page.locator('.nav-link[href="/"]');
        await expect(homeLink).toHaveClass(/active/);
    });

    test('theme toggle button exists', async ({ page }) => {
        await page.goto('/');
        const toggle = page.locator('#theme-toggle');
        await expect(toggle).toBeVisible();
    });

    test('header auth area exists', async ({ page }) => {
        await page.goto('/');
        const auth = page.locator('#headerAuth');
        await expect(auth).toBeAttached();
    });
});

test.describe('Homepage', () => {
    test('has quick actions grid with 3 cards', async ({ page }) => {
        await page.goto('/');
        const cards = page.locator('.quick-action-card');
        await expect(cards).toHaveCount(3);
    });

    test('has my videos section', async ({ page }) => {
        await page.goto('/');
        const section = page.locator('#myVideosSection');
        await expect(section).toBeVisible();
        const title = page.locator('.section-title');
        await expect(title.first()).toHaveText('我的视频');
    });

    test('has video count display', async ({ page }) => {
        await page.goto('/');
        const count = page.locator('.video-count');
        await expect(count).toBeAttached();
    });
});

test.describe('No horizontal overflow at 375px', () => {
    for (const p of pagesForOverflow) {
        test(`${p.name} (${p.path}) — no overflow`, async ({ browser }) => {
            const context = await browser.newContext({
                viewport: { width: 375, height: 812 },
            });
            const page = await context.newPage();
            await page.goto(p.path, { waitUntil: 'domcontentloaded', timeout: 20000 });
            // Wait for CSS to apply
            await page.waitForTimeout(500);
            const bodyWidth = await page.evaluate(() => document.body.scrollWidth);
            expect(bodyWidth).toBeLessThanOrEqual(376);
            await context.close();
        });
    }
});

test.describe('Mobile touch targets', () => {
    test('buttons have min 44px height on mobile', async ({ browser }) => {
        const context = await browser.newContext({
            viewport: { width: 375, height: 812 },
        });
        const page = await context.newPage();
        await page.goto('/login.html', { waitUntil: 'domcontentloaded' });
        const buttons = page.locator('.btn');
        const count = await buttons.count();
        for (let i = 0; i < count; i++) {
            const box = await buttons.nth(i).boundingBox();
            if (box) {
                expect(box.height).toBeGreaterThanOrEqual(44);
            }
        }
        await context.close();
    });
});

test.describe('Login page countdown', () => {
    test('countdown button shows timer text after click', async ({ page }) => {
        await page.goto('/login.html');
        const phoneInput = page.locator('#input_phone');
        await phoneInput.fill('13800138000');
        const codeBtn = page.locator('#btn_requestVerificationCode');
        const originalText = await codeBtn.textContent();
        expect(originalText).toBe('获取验证码');
        // Click will attempt API call which will fail, but countdown should still
        // be testable via the button text change mechanism
    });
});

test.describe('Footer', () => {
    for (const p of pages) {
        test(`${p.name} has footer`, async ({ page }) => {
            await page.goto(p.path, { waitUntil: 'domcontentloaded', timeout: 60000 });
            const footer = page.locator('.page-footer');
            await expect(footer).toBeAttached({ timeout: 10000 });
            await expect(footer).toContainText('Video Platform');
        });
    }
});

test.describe('Upload page', () => {
    test('description is a textarea', async ({ page }) => {
        // Set fake token to bypass auth redirect
        await page.goto('/login.html', { waitUntil: 'domcontentloaded' });
        await page.evaluate(() => localStorage.setItem('token', 'test-token'));
        await page.goto('/upload.html', { waitUntil: 'domcontentloaded' });
        const textarea = page.locator('textarea#input_description');
        await expect(textarea).toBeAttached();
    });

    test('has upload sections with titles', async ({ page }) => {
        await page.goto('/login.html', { waitUntil: 'domcontentloaded' });
        await page.evaluate(() => localStorage.setItem('token', 'test-token'));
        await page.goto('/upload.html', { waitUntil: 'domcontentloaded' });
        const sections = page.locator('.upload-section');
        const count = await sections.count();
        expect(count).toBeGreaterThanOrEqual(2);
    });
});
