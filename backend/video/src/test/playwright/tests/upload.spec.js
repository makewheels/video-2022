const { test, expect } = require('@playwright/test');

test.describe('Upload page', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('token', 'test-token'));
    await page.route('**/axios*.js', route => route.fulfill({
      status: 200, contentType: 'application/javascript',
      body: 'window.axios = { post: () => Promise.resolve({data:{code:0,data:{}}}), get: () => Promise.resolve({data:{code:0,data:[]}}) };'
    }));
    await page.route('**/aliyun-oss-sdk*.js', route => route.fulfill({
      status: 200, contentType: 'application/javascript',
      body: 'window.OSS = function(){}; OSS.prototype.multipartUpload = () => Promise.resolve({res:{status:200}});'
    }));
    await page.route('**/user/getUserByToken*', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: { id: 'test' } })
    }));
    await page.route('**/playlist/getMyPlaylistByPage*', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: [] })
    }));
    await page.goto('/upload.html');
  });

  test('has page title', async ({ page }) => {
    await expect(page).toHaveTitle(/上传/);
  });

  test('has upload zone', async ({ page }) => {
    const zone = page.locator('.upload-zone');
    await expect(zone).toBeVisible();
  });

  test('has title input', async ({ page }) => {
    const input = page.locator('#input_title');
    await expect(input).toBeVisible();
  });

  test('has progress bar', async ({ page }) => {
    const bar = page.locator('#progress-bar');
    await expect(bar).toBeVisible();
  });

  test('has playlist selector', async ({ page }) => {
    const select = page.locator('#select_playlist');
    await expect(select).toBeVisible();
  });

  // === UX 反馈测试 ===

  test('file selection shows filename and size in upload zone', async ({ page }) => {
    const fileChooserPromise = page.waitForEvent('filechooser');
    await page.locator('#uploadZone').click();
    const fileChooser = await fileChooserPromise;
    // Create a small test buffer
    const buffer = Buffer.alloc(1024 * 500, 'x'); // 500KB
    await fileChooser.setFiles({
      name: 'test-video.mp4',
      mimeType: 'video/mp4',
      buffer: buffer
    });

    // Upload zone should show filename
    const zoneText = page.locator('#uploadZoneText');
    await expect(zoneText).toContainText('test-video.mp4');
    // Upload zone icon should change from folder to file
    const zoneIcon = page.locator('#uploadZoneIcon');
    await expect(zoneIcon).toHaveText('📄');
  });

  test('clicking modify without video shows error toast', async ({ page }) => {
    await page.locator('#btn_update').click();
    // Should show error toast "请先选择并上传文件"
    const toast = page.locator('.toast-error');
    await expect(toast).toBeVisible({ timeout: 3000 });
    await expect(toast).toContainText('请先选择');
  });

  test('clicking copy without URL shows error toast', async ({ page }) => {
    await page.locator('#btn_copy').click();
    const toast = page.locator('.toast-error');
    await expect(toast).toBeVisible({ timeout: 3000 });
    await expect(toast).toContainText('暂无可复制');
  });

  test('playlist button is disabled when no playlists', async ({ page }) => {
    // Wait for playlist to load (empty list mock already set up)
    await page.waitForTimeout(500);
    const btn = page.locator('#btn_addPlaylistItem');
    await expect(btn).toBeDisabled();
  });

  test('add to playlist without video shows error toast', async ({ page }) => {
    // Use a fresh page with axios mock that returns playlists
    const newPage = await page.context().newPage();
    await newPage.addInitScript(() => localStorage.setItem('token', 'test-token'));
    await newPage.route('**/axios*.js', route => route.fulfill({
      status: 200, contentType: 'application/javascript',
      body: `window.axios = {
        post: () => Promise.resolve({data:{code:0,data:{}}}),
        get: (url) => {
          if (url.includes('getMyPlaylistByPage')) {
            return Promise.resolve({data:{code:0,data:[{id:'pl1',title:'Test'}]}});
          }
          if (url.includes('getUserByToken')) {
            return Promise.resolve({data:{code:0,data:{id:'test'}}});
          }
          return Promise.resolve({data:{code:0,data:{}}});
        }
      };`
    }));
    await newPage.route('**/aliyun-oss-sdk*.js', route => route.fulfill({
      status: 200, contentType: 'application/javascript',
      body: 'window.OSS = function(){};'
    }));
    await newPage.goto('/upload.html');
    await newPage.waitForTimeout(500);

    const btn = newPage.locator('#btn_addPlaylistItem');
    await expect(btn).toBeEnabled({ timeout: 3000 });
    await btn.click();
    const toast = newPage.locator('.toast-error');
    await expect(toast).toBeVisible({ timeout: 3000 });
    await expect(toast).toContainText('请先选择');
    await newPage.close();
  });
});
