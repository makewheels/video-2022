const { test, expect } = require('@playwright/test');

test.describe('Video.js 播放器', () => {
  test.beforeEach(async ({ page }) => {
    // Mock axios
    await page.route('**/axios*.js', route => route.fulfill({
      status: 200, contentType: 'application/javascript',
      body: `window.axios = { get: function(url) {
        return fetch(url).then(function(r) { return r.json().then(function(d) { return {data: d}; }); });
      }, post: function(url, data, opts) {
        return fetch(url, {method:'POST',body:JSON.stringify(data),headers:{'Content-Type':'application/json'}}).then(function(r) { return r.json().then(function(d) { return {data: d}; }); });
      }, create: function() { return window.axios; }};`
    }));

    // Mock Video.js
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

    // Mock global.js dependencies
    await page.route('**/global.js', route => route.continue());

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
        videoId: 'v_test', videoStatus: 'CREATED', coverUrl: '',
        multivariantPlaylistUrl: '', progressInMillis: 0
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

  test('页面加载Video.js而非Aliplayer', async ({ page }) => {
    const vjsLoaded = await page.evaluate(() => typeof window.videojs === 'function');
    expect(vjsLoaded).toBeTruthy();

    const aliLoaded = await page.evaluate(() => typeof window.Aliplayer === 'function');
    expect(aliLoaded).toBeFalsy();
  });

  test('播放器容器使用video标签', async ({ page }) => {
    const videoElement = page.locator('video#player-con');
    await expect(videoElement).toBeAttached();
  });

  test('Video.js CSS已加载', async ({ page }) => {
    const vjsLink = page.locator('link[href*="video-js.css"]');
    await expect(vjsLink).toBeAttached();
  });

  test('没有Aliplayer CSS', async ({ page }) => {
    const aliLink = page.locator('link[href*="aliplayer"]');
    await expect(aliLink).toHaveCount(0);
  });

  test('getInitSeekTimeInSeconds函数存在', async ({ page }) => {
    const fnExists = await page.evaluate(() => typeof window.getInitSeekTimeInSeconds === 'function');
    expect(fnExists).toBeTruthy();
  });

  test('initKeyboardShortcuts函数存在', async ({ page }) => {
    const fnExists = await page.evaluate(() => typeof window.initKeyboardShortcuts === 'function');
    expect(fnExists).toBeTruthy();
  });

  test('复制按钮存在且可见', async ({ page }) => {
    const copyBtn = page.locator('#btn_copyCurrentTime');
    await expect(copyBtn).toBeVisible();
    await expect(copyBtn).toContainText('复制');
  });
});

test.describe('时间跳转 t= 参数', () => {
  test('t参数被getInitSeekTimeInSeconds读取', async ({ page }) => {
    // Set up mocks
    await page.route('**/axios*.js', route => route.fulfill({
      status: 200, contentType: 'application/javascript',
      body: `window.axios = { get: function() { return Promise.resolve({data:{}}); }, post: function() { return Promise.resolve({data:{}}); } };`
    }));
    await page.route('**/video.min.js', route => route.fulfill({
      status: 200, contentType: 'application/javascript',
      body: `window.videojs = function() { var p = { on:function(){return p;}, src:function(){}, currentTime:function(){return 0;}, duration:function(){return 100;}, paused:function(){return true;}, play:function(){return Promise.resolve();}, pause:function(){}, volume:function(){return 1;}, muted:function(){return false;}, isFullscreen:function(){return false;}, requestFullscreen:function(){}, exitFullscreen:function(){}, httpSourceSelector:function(){}, qualityLevels:function(){var q=[];q.on=function(){};q.selectedIndex=-1;return q;} }; return p; }; window.videojs.getPlayer = function(){ return null; };`
    }));
    await page.route('**/video-js.css', r => r.fulfill({ status: 200, contentType: 'text/css', body: '' }));
    await page.route('**/videojs-contrib-quality-levels*', r => r.fulfill({ status: 200, contentType: 'application/javascript', body: '' }));
    await page.route('**/videojs-http-source-selector*', r => r.fulfill({ status: 200, contentType: 'application/javascript', body: '' }));

    await page.addInitScript(() => {
      localStorage.setItem('clientId', 'test-client');
      sessionStorage.setItem('sessionId', 'test-session');
    });

    await page.goto('/watch?v=test123&t=42');
    await page.waitForTimeout(2000);

    const seekTime = await page.evaluate(() => {
      return window.getInitSeekTimeInSeconds({
        videoId: 'v_test', progressInMillis: 0
      });
    });
    expect(seekTime).toBe(42);
  });
});
