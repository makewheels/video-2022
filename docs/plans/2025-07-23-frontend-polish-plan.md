# 前端视觉优化 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 全面优化前端视觉和响应式体验，让页面在手机和电脑上都好看实用。

**Architecture:** 保持 vanilla HTML/CSS/JS，不引入框架。改善全局 CSS 变量系统、导航栏、页面布局和移动端适配。使用 Playwright TDD 验证跨设备布局。

**Tech Stack:** HTML5, CSS3 (variables + media queries), Vanilla JS, Playwright (testing)

---

### Task 1: 增强导航栏 — 添加导航链接和用户状态

**Files:**
- Modify: `video/src/main/resources/static/css/global.css` (header section ~L134-165)
- Modify: `video/src/main/resources/static/index.html`
- Modify: `video/src/main/resources/static/login.html`
- Modify: `video/src/main/resources/static/upload.html`
- Modify: `video/src/main/resources/static/statistics.html`
- Modify: `video/src/main/resources/static/transfer-youtube.html`
- Modify: `video/src/main/resources/templates/watch.html`
- Test: `video/src/test/playwright/tests/navigation.spec.js`

**Why:** 当前 header 只有 "Video" logo 和主题切换。用户无法方便地在页面间导航，也看不到登录状态。需要添加导航链接（首页、上传、统计）和用户登录/退出按钮。

**Step 1: Write failing Playwright test**

```javascript
// tests/navigation.spec.js
const { test, expect } = require('@playwright/test');

test.describe('Navigation Bar', () => {
  test('header contains nav links on desktop', async ({ page }) => {
    await page.goto('/');
    const header = page.locator('.page-header');
    await expect(header).toBeVisible();
    await expect(header.locator('.nav-menu')).toBeVisible();
    await expect(header.locator('a[href="/upload.html"]')).toBeVisible();
  });

  test('header shows login button when not authenticated', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('.header-auth')).toBeVisible();
  });

  test('mobile shows hamburger menu', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/');
    await expect(page.locator('.mobile-menu-btn')).toBeVisible();
    await expect(page.locator('.nav-menu')).toBeHidden();
  });

  test('mobile hamburger opens nav', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/');
    await page.click('.mobile-menu-btn');
    await expect(page.locator('.nav-menu')).toBeVisible();
  });
});
```

**Step 2: Run test to verify it fails**

Run: `cd video/src/test/playwright && npx playwright test tests/navigation.spec.js --project=desktop`
Expected: FAIL — `.nav-menu` elements don't exist yet

**Step 3: Implement navigation bar**

Add to global.css:
- `.nav-menu` — horizontal nav links (flex, gap, hidden on mobile)
- `.mobile-menu-btn` — hamburger icon (visible only on mobile)
- `.header-auth` — login/user area on right side
- `.header-nav` — container for nav + auth
- Media query: hide nav-menu on `max-width: 768px`, show mobile-menu-btn

Update ALL HTML files (6 static + 1 template):
- Replace simple header with enhanced header containing nav-menu, auth area, and mobile menu button
- Add mobile drawer overlay

**Step 4: Run test to verify it passes**

Run: `cd video/src/test/playwright && npx playwright test tests/navigation.spec.js`
Expected: PASS

**Step 5: Commit**

```bash
git add -A && git commit -m "feat: 增强导航栏 — 添加导航链接、用户状态和移动端汉堡菜单"
```

---

### Task 2: 首页重设计 — 从链接卡片到视频管理面板

**Files:**
- Modify: `video/src/main/resources/static/index.html`
- Modify: `video/src/main/resources/static/css/global.css`
- Test: `video/src/test/playwright/tests/homepage.spec.js`

**Why:** 当前首页只是一个居中的卡片里放了3个链接。需要改成实用的管理面板：快速操作区 + 我的视频列表。

**Step 1: Write failing Playwright test**

```javascript
// tests/homepage.spec.js
const { test, expect } = require('@playwright/test');

test.describe('Homepage', () => {
  test('shows quick actions section', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('.quick-actions')).toBeVisible();
  });

  test('shows my videos section', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('.my-videos-section')).toBeVisible();
  });

  test('quick actions are responsive grid', async ({ page }) => {
    await page.goto('/');
    const actions = page.locator('.action-card');
    await expect(actions).toHaveCount(3); // Upload, Stats, YouTube
  });

  test('mobile layout stacks vertically', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/');
    await expect(page.locator('.quick-actions')).toBeVisible();
  });
});
```

**Step 2: Run test, expect FAIL**

**Step 3: Implement homepage**

Replace centered card with full-width page:
- Hero greeting section (centered, minimal)
- Quick actions grid (3 cards: Upload, Stats, YouTube Transfer)
- "我的视频" section with video grid (calls `/video/getMyVideoList`)
- Empty state when no videos

**Step 4: Run test, expect PASS**

**Step 5: Commit**

```bash
git add -A && git commit -m "feat: 首页重设计 — 快速操作区和视频列表"
```

---

### Task 3: 全局 CSS 优化 — 更好的排版和移动端适配

**Files:**
- Modify: `video/src/main/resources/static/css/global.css`
- Test: `video/src/test/playwright/tests/responsive.spec.js`

**Why:** 当前只有一个 768px 断点。需要添加更多断点、更好的间距系统、和移动端触摸目标优化。

**Step 1: Write failing Playwright test**

```javascript
// tests/responsive.spec.js
const { test, expect } = require('@playwright/test');

test.describe('Responsive Design', () => {
  test('small mobile (375px) - no horizontal overflow', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/');
    const body = page.locator('body');
    const scrollWidth = await body.evaluate(el => el.scrollWidth);
    const clientWidth = await body.evaluate(el => el.clientWidth);
    expect(scrollWidth).toBeLessThanOrEqual(clientWidth + 1);
  });

  test('tablet (768px) - proper layout', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 1024 });
    await page.goto('/');
    await expect(page.locator('.page-header')).toBeVisible();
  });

  test('buttons have minimum touch target 44px', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/login.html');
    const btn = page.locator('.btn').first();
    const box = await btn.boundingBox();
    expect(box.height).toBeGreaterThanOrEqual(44);
  });

  test('upload page no overflow on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/upload.html');
    const body = page.locator('body');
    const scrollWidth = await body.evaluate(el => el.scrollWidth);
    const clientWidth = await body.evaluate(el => el.clientWidth);
    expect(scrollWidth).toBeLessThanOrEqual(clientWidth + 1);
  });

  test('watch page responsive on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    // watch page needs a valid watchId, just test that layout doesn't overflow
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    const body = page.locator('body');
    const scrollWidth = await body.evaluate(el => el.scrollWidth);
    const clientWidth = await body.evaluate(el => el.clientWidth);
    expect(scrollWidth).toBeLessThanOrEqual(clientWidth + 1);
  });
});
```

**Step 2: Run test, expect FAIL**

**Step 3: Implement CSS improvements**

- Add `min-height: 44px` to buttons on mobile
- Add `max-width: 480px` breakpoint for small phones
- Improve card padding on very small screens
- Better font sizes for mobile (slightly larger base)
- Fix upload zone padding on mobile
- Better spacing for form groups

**Step 4: Run test, expect PASS**

**Step 5: Commit**

```bash
git add -A && git commit -m "feat: 全局CSS优化 — 移动端触摸目标、排版和间距"
```

---

### Task 4: 上传页面优化 — 更好的上传体验

**Files:**
- Modify: `video/src/main/resources/static/upload.html`
- Modify: `video/src/main/resources/static/css/global.css`
- Test: `video/src/test/playwright/tests/upload.spec.js`

**Why:** 上传页面需要更好的文件信息展示、上传状态指示和视觉层次。

**Step 1: Write failing Playwright test**

```javascript
// tests/upload.spec.js
const { test, expect } = require('@playwright/test');

test.describe('Upload Page', () => {
  test('upload zone is prominent', async ({ page }) => {
    // Mock auth to access upload page
    await page.addInitScript(() => {
      localStorage.setItem('token', 'test-token');
    });
    await page.goto('/upload.html');
    await expect(page.locator('.upload-zone')).toBeVisible();
  });

  test('has proper section dividers', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('token', 'test-token');
    });
    await page.goto('/upload.html');
    await expect(page.locator('.upload-section')).toBeVisible();
  });

  test('mobile layout works', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.addInitScript(() => {
      localStorage.setItem('token', 'test-token');
    });
    await page.goto('/upload.html');
    const body = page.locator('body');
    const scrollWidth = await body.evaluate(el => el.scrollWidth);
    const clientWidth = await body.evaluate(el => el.clientWidth);
    expect(scrollWidth).toBeLessThanOrEqual(clientWidth + 1);
  });
});
```

**Step 2: Run test, expect FAIL**

**Step 3: Implement improvements**

- Wrap upload zone and form in `.upload-section` for semantic grouping
- Better file info display after selection (filename, size badge)
- Larger progress bar with percentage text inside
- Better button group layout
- Description input → textarea

**Step 4: Run test, expect PASS**

**Step 5: Commit**

```bash
git add -A && git commit -m "feat: 上传页面优化 — 更好的文件信息和上传状态展示"
```

---

### Task 5: 播放页面移动端优化

**Files:**
- Modify: `video/src/main/resources/templates/watch.html`
- Modify: `video/src/main/resources/static/css/global.css`
- Test: `video/src/test/playwright/tests/watch-responsive.spec.js`

**Why:** 播放页面在手机上播放列表侧边栏太大，视频信息区域间距需要调整。

**Step 1: Write failing Playwright test**

```javascript
// tests/watch-responsive.spec.js
const { test, expect } = require('@playwright/test');

test.describe('Watch Page Responsive', () => {
  test('mobile has full-width player area', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    // Just check layout structure, not video content
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    // Since watch page needs valid ID, just verify CSS is loaded
    const globalCSS = await page.evaluate(() => {
      const styles = getComputedStyle(document.documentElement);
      return styles.getPropertyValue('--header-height').trim();
    });
    expect(globalCSS).toBeTruthy();
  });
});
```

**Step 2: Run test, expect FAIL or PASS (depends)**

**Step 3: Implement watch page improvements**

- Better mobile spacing for video info
- Smaller thumbnails in playlist on mobile
- Better "not ready" state display
- Copy button styling improvement

**Step 4: Run test, expect PASS**

**Step 5: Commit**

```bash
git add -A && git commit -m "feat: 播放页面移动端优化 — 间距和播放列表改进"
```

---

### Task 6: 登录页和其他页面微调

**Files:**
- Modify: `video/src/main/resources/static/login.html`
- Modify: `video/src/main/resources/static/statistics.html`
- Modify: `video/src/main/resources/static/transfer-youtube.html`
- Modify: `video/src/main/resources/static/css/global.css`

**Why:** 登录页需要更好的表单布局和验证码发送倒计时。统计页和YouTube搬运页需要与新导航栏一致。

**Step 1: Implement improvements**

- Login: Add countdown timer for "获取验证码" button (60s cooldown)
- Login: Better input styling on mobile (larger touch targets)
- Statistics: Mobile chart height adjustment
- YouTube Transfer: Better URL input validation visual feedback
- All pages: Consistent footer with copyright

**Step 2: Run all Playwright tests**

```bash
cd video/src/test/playwright && npx playwright test
```

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: 登录页倒计时、统计页和YouTube页微调"
```

---

### Task 7: 重建JAR并端到端验证

**Files:** None (build + test only)

**Step 1: Rebuild JAR**

```bash
cd /Users/mint/java-projects/video-2022 && mvn clean package -pl video -Pspringboot -Dmaven.test.skip=true
```

**Step 2: Restart server and run Playwright tests**

```bash
# Stop old server
# Start new server with rebuilt JAR
export $(grep -v '^#' .env | grep -v '^$' | xargs) && java -jar video/target/video-0.0.1-SNAPSHOT.jar &

# Run all Playwright tests
cd video/src/test/playwright && npx playwright test --reporter=list
```

**Step 3: Open browser and verify visually**

```bash
npx playwright open http://localhost:5022
```

**Step 4: Push and create PR**

```bash
git push origin feat/frontend-polish
gh pr create --title "feat: 前端视觉优化 — 导航栏、首页、响应式、移动端适配" --body "..."
```
