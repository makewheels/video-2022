# 第1期：播放器核心体验 设计文档

> **状态:** 📐 设计完成，待实施

## 目标

将视频播放体验从基础的 Aliplayer 升级为功能完善的 Video.js 播放器，新增分辨率手动切换、键盘快捷键、进度条缩略图预览、记忆播放位置等功能，并建立播放时长统计和退出记录体系。

## 功能清单

| # | 功能 | 来源 | 优先级 |
|---|------|------|--------|
| 1 | Video.js m3u8 播放器 | 待办 | P0 |
| 2 | 手动分辨率切换（720p/1080p） | 待办 | P0 |
| 3 | 键盘快捷键 | 新增 | P0 |
| 4 | 全屏播放 | 已有 | P0 |
| 5 | 时间跳转 t=21 | 待办 | P1 |
| 6 | 记忆播放位置 | 待办 | P1 |
| 7 | 进度条缩略图预览 | 新增 | P2 |
| 8 | 播放时长统计 | 待办 | P1 |
| 9 | 浏览退出记录 | 待办 | P1 |

## 架构设计

### 1. 播放器替换：Aliplayer → Video.js

**技术选型：**
- 引擎：Video.js 8.x + videojs-http-streaming (VHS)
- HLS 解析：Video.js 内置 VHS（基于 hls.js）
- CDN 引入，不使用 npm

**加载方式：**
```html
<link href="https://vjs.zencdn.net/8.10.0/video-js.css" rel="stylesheet">
<script src="https://vjs.zencdn.net/8.10.0/video.min.js"></script>
```

**初始化：**
```javascript
const player = videojs('player', {
    controls: true,
    autoplay: true,
    preload: 'auto',
    fluid: true,               // 自适应容器
    playsinline: true,          // 移动端内联播放
    html5: {
        vhs: {
            overrideNative: true  // 使用 VHS 引擎而非原生 HLS
        }
    }
});
player.src({ type: 'application/x-mpegURL', src: m3u8Url });
```

### 2. 分辨率手动切换

Video.js VHS 自动解析 multivariant playlist 中的多分辨率流。使用 `videojs-http-source-selector` 插件提供 UI：

```html
<script src="https://unpkg.com/videojs-http-source-selector/dist/videojs-http-source-selector.js"></script>
```

```javascript
player.httpSourceSelector();  // 在控制栏添加分辨率选择菜单
```

显示效果：控制栏右侧出现齿轮图标，下拉菜单列出 Auto / 720p / 1080p。

### 3. 键盘快捷键

使用 `videojs-hotkeys` 插件或自行实现：

| 按键 | 功能 |
|------|------|
| 空格 / K | 播放/暂停 |
| ← | 后退 5 秒 |
| → | 前进 5 秒 |
| J | 后退 10 秒 |
| L | 前进 10 秒 |
| F | 全屏切换 |
| M | 静音切换 |
| ↑ | 音量 +10% |
| ↓ | 音量 -10% |

### 4. 时间跳转 t=21

**URL 格式：** `?v=watchId&t=21`（秒数）

**替代旧参数：** 移除 `seekTimeInMills`，统一用 `t`（秒）。

**逻辑优先级：**
1. URL 参数 `t` → 精确跳转
2. 后端存储的进度 `progressInMillis` → 继续播放
3. 默认从头开始

**分享复制：** "复制当前时间链接"按钮生成 `watchUrl?t=123` 格式。

### 5. 记忆播放位置

**双重存储策略：**
- **前端 localStorage**：即时保存，无网络延迟。Key: `video_progress_{watchId}`
- **后端 API**：心跳上报时同步保存到 PlaybackSession（见下文）

**恢复优先级：**
1. URL `t=` 参数（用户明确指定）
2. 后端 PlaybackSession 记录的进度
3. localStorage 缓存的进度
4. 从头开始

**保存时机：**
- 每 15 秒心跳时保存
- 页面退出时通过 sendBeacon 保存
- 用户暂停时保存

### 6. 进度条缩略图预览

**实现方案：** 使用 `videojs-sprite-thumbnails` 插件。

**缩略图生成：** 后端转码完成后，通过阿里云 MPS 生成雪碧图（sprite sheet）—— 每 N 秒截一帧，拼成一张大图。

**前端配置：**
```javascript
player.spriteThumbnails({
    url: spriteSheetUrl,      // 雪碧图 URL
    width: 160,               // 单帧宽度
    height: 90,               // 单帧高度
    interval: 5               // 每帧间隔（秒）
});
```

**注意：** 缩略图生成依赖后端转码pipeline扩展，本期先搭建前端框架，后端生成作为可选增强。

### 7. 播放时长统计 + 浏览退出记录

**新建 MongoDB Collection：`playbackSession`**

```json
{
    "_id": "ps_xxx",
    "watchId": "w_xxx",
    "videoId": "v_xxx",
    "userId": "u_xxx",          // 可能为 null（匿名用户）
    "clientId": "c_xxx",
    "sessionId": "s_xxx",
    "startTime": "2026-03-07T12:00:00Z",
    "endTime": "2026-03-07T12:05:30Z",
    "totalDurationMs": 330000,   // 实际观看时长（去除暂停）
    "maxProgressMs": 280000,     // 最远观看进度
    "currentProgressMs": 250000, // 当前播放位置
    "resolution": "720p",
    "exitType": "CLOSE_TAB",     // CLOSE_TAB / NAVIGATE_AWAY / PAUSE_LEAVE
    "heartbeats": 22,            // 心跳次数
    "createTime": "...",
    "updateTime": "..."
}
```

**前端心跳上报：**
```
每 15 秒 → POST /playback/heartbeat
{
    "watchId": "w_xxx",
    "clientId": "c_xxx",
    "sessionId": "s_xxx",
    "currentTimeMs": 125000,
    "isPlaying": true,
    "resolution": "720p"
}
```

**退出上报：**
```javascript
document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') {
        navigator.sendBeacon('/playback/exit', JSON.stringify({
            playbackSessionId: currentSessionId,
            currentTimeMs: player.currentTime() * 1000,
            exitType: 'CLOSE_TAB'
        }));
    }
});
```

**后端 API：**

| 接口 | 方法 | 说明 |
|------|------|------|
| `/playback/start` | POST | 开始播放，创建 PlaybackSession |
| `/playback/heartbeat` | POST | 心跳上报，更新进度和时长 |
| `/playback/exit` | POST | 退出上报，记录退出类型和最终进度 |

## 数据流

```
用户打开播放页
  → GET /watchController/getWatchInfo → 获取 m3u8 URL + 上次进度
  → Video.js 初始化 + 设置源 + 跳转到上次进度
  → POST /playback/start → 创建 PlaybackSession
  → 每 15 秒 POST /playback/heartbeat → 更新进度
  → 退出时 sendBeacon /playback/exit → 记录退出
```

## 影响范围

| 文件 | 变更 |
|------|------|
| `watch.html` | 重写：Aliplayer → Video.js，新增心跳/退出逻辑 |
| `global.css` | 新增 Video.js 主题覆盖样式 |
| 新建 `PlaybackSession.java` | 实体类 |
| 新建 `PlaybackSessionRepository.java` | 数据访问 |
| 新建 `PlaybackController.java` | 3 个接口 |
| 新建 `PlaybackService.java` | 业务逻辑 |
| `WatchService.java` | 修改 getWatchInfo 返回上次进度 |
| `WatchController.java` | 可能调整 |

## 不做什么（YAGNI）

- ❌ 弹幕功能（第2期用户功能）
- ❌ 倍速播放（不在待办列表中）
- ❌ 画中画（优先级低）
- ❌ 雪碧图后端生成（本期只搭前端框架，后续扩展转码pipeline）
