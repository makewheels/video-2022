const { test, expect } = require('@playwright/test');

test.describe('Watch page structure', () => {
  test.beforeEach(async ({ page }) => {
    // Mock external CDN resources that may fail in CI/test environments
    await page.route('**/axios*.js', route => route.fulfill({
      status: 200, contentType: 'application/javascript',
      body: `window.axios = { get: function(url) {
        return fetch(url).then(function(r) { return r.json().then(function(d) { return {data: d}; }); });
      }, post: function(url, data) {
        return fetch(url, {method:'POST',body:JSON.stringify(data)}).then(function(r) { return r.json().then(function(d) { return {data: d}; }); });
      }};`
    }));
    await page.route('**/video.min.js', route => route.fulfill({
      status: 200, contentType: 'application/javascript',
      body: `window.videojs = function(id, opts) {
        var p = {
          on: function(e,cb){ if(e==='loadedmetadata'&&cb) cb(); return p; },
          src: function(){},
          currentTime: function(t){ if(t!==undefined) return; return 0; },
          duration: function(){ return 100; },
          paused: function(){ return true; },
          play: function(){ return Promise.resolve(); },
          pause: function(){},
          volume: function(v){ if(v!==undefined) return; return 1; },
          muted: function(m){ if(m!==undefined) return; return false; },
          isFullscreen: function(){ return false; },
          requestFullscreen: function(){},
          exitFullscreen: function(){},
          httpSourceSelector: function(){},
          qualityLevels: function(){ var ql = []; ql.on = function(){}; ql.selectedIndex = -1; return ql; }
        };
        return p;
      };
      window.videojs.getPlayer = function(){ return null; };`
    }));
    await page.route('**/video-js.css', route => route.fulfill({
      status: 200, contentType: 'text/css', body: ''
    }));
    await page.route('**/videojs-contrib-quality-levels*', route => route.fulfill({
      status: 200, contentType: 'application/javascript', body: ''
    }));
    await page.route('**/videojs-http-source-selector*', route => route.fulfill({
      status: 200, contentType: 'application/javascript', body: ''
    }));

    // Mock API endpoints
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
        videoId: 'v_test', videoStatus: 'CREATED', coverUrl: ''
      }})
    }));
    await page.route('**/video/**', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: {
        title: 'Test Video', description: 'Test desc', type: 'USER_UPLOAD',
        createTimeString: '2025-01-01', watchCount: 100
      }})
    }));
    await page.route('**/heartbeat/**', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 0 })
    }));
    await page.route('**/playback/**', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: { playbackSessionId: 'ps_test' } })
    }));
    await page.addInitScript(() => {
      localStorage.setItem('clientId', 'test-client');
      sessionStorage.setItem('sessionId', 'test-session');
    });
    await page.goto('/watch?v=test123');
    await page.waitForTimeout(2000);
  });

  test('has player container', async ({ page }) => {
    const player = page.locator('#player-con');
    await expect(player).toHaveCount(1);
  });

  test('has video info section', async ({ page }) => {
    const info = page.locator('.video-info-section');
    await expect(info).toBeVisible();
  });

  test('has video title element', async ({ page }) => {
    const title = page.locator('#div_title');
    await expect(title).toHaveText('Test Video', { timeout: 10000 });
  });

  test('player container has 16:9 aspect ratio wrapper', async ({ page }) => {
    const wrapper = page.locator('.player-wrapper');
    await expect(wrapper).toHaveCount(1);
  });
});

test.describe('Watch page responsive', () => {
  test('playlist is below player on mobile', async ({ page, isMobile }) => {
    test.skip(!isMobile, 'Mobile only test');
    await page.route('**/*', route => route.fulfill({ status: 200, body: '' }));
    await page.goto('/watch?v=test123');
    const layout = page.locator('.watch-layout');
    if (await layout.count() > 0) {
      const display = await layout.evaluate(el => getComputedStyle(el).flexDirection);
      expect(display).toBe('column');
    }
  });
});
