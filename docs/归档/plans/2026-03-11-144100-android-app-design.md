# Android 原生客户端设计文档

> 日期：2026-03-11
> 状态：已确认
> 技术方案：Kotlin + Jetpack Compose + Material Design 3
> 目标：与 Web 端功能完全一致（除统计页外）

---

## 项目定位

为 video-2022 视频平台构建 Android 原生客户端。功能范围与 Web 端（React SPA）完全对齐，技术栈采用现代 Android 开发标准。iOS 端后续独立开发（Swift）。

---

## 技术栈

| 用途 | 库/框架 | 版本策略 |
|------|---------|---------|
| 语言 | Kotlin | 最新稳定版 |
| UI | Jetpack Compose + Material 3 | BOM 管理 |
| 导航 | Navigation Compose | 单 Activity |
| 网络 | Retrofit + OkHttp + Gson | OkHttp Interceptor 统一注入 token |
| 视频播放 | Media3 ExoPlayer | 原生 HLS 支持 |
| 图片加载 | Coil Compose | - |
| 异步 | Kotlin Coroutines + Flow | - |
| DI | Hilt | - |
| 后台上传 | WorkManager | 支持杀进程后恢复 |
| OSS 直传 | 阿里云 OSS Android SDK | STS 临时凭证 |
| 图表 | 无（统计功能已移除）| - |

---

## 项目结构

```
android/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/github/makewheels/video2022/
│       │   ├── VideoApp.kt                    # Application，Hilt 入口
│       │   ├── MainActivity.kt                # 单 Activity，承载 Compose NavHost
│       │   ├── navigation/
│       │   │   └── AppNavGraph.kt             # 路由定义，所有 Screen 路由常量
│       │   ├── ui/
│       │   │   ├── login/
│       │   │   │   ├── LoginScreen.kt         # 手机号 + 验证码登录
│       │   │   │   └── LoginViewModel.kt
│       │   │   ├── home/
│       │   │   │   ├── HomeScreen.kt          # 视频列表，下拉刷新，分页
│       │   │   │   └── HomeViewModel.kt
│       │   │   ├── watch/
│       │   │   │   ├── WatchScreen.kt         # 播放页：ExoPlayer + 评论 + 点赞
│       │   │   │   ├── WatchViewModel.kt
│       │   │   │   └── VideoPlayerComposable.kt
│       │   │   ├── upload/
│       │   │   │   ├── UploadScreen.kt        # 选择/拍摄 → 填信息 → 提交
│       │   │   │   └── UploadViewModel.kt
│       │   │   ├── myvideos/
│       │   │   │   ├── MyVideosScreen.kt      # 我的视频列表
│       │   │   │   └── MyVideosViewModel.kt
│       │   │   ├── edit/
│       │   │   │   ├── EditScreen.kt          # 编辑视频标题/描述
│       │   │   │   └── EditViewModel.kt
│       │   │   ├── playlist/
│       │   │   │   ├── PlaylistScreen.kt      # 播放列表管理
│       │   │   │   ├── PlaylistDetailScreen.kt
│       │   │   │   └── PlaylistViewModel.kt
│       │   │   ├── youtube/
│       │   │   │   ├── YouTubeScreen.kt       # YouTube 下载
│       │   │   │   └── YouTubeViewModel.kt
│       │   │   └── components/                # 公共 Compose 组件
│       │   │       ├── VideoCard.kt           # 视频卡片（缩略图 + 标题 + 时长）
│       │   │       ├── CommentSheet.kt        # 评论 BottomSheet
│       │   │       ├── CommentItem.kt
│       │   │       ├── LikeButtons.kt         # 点赞/踩按钮
│       │   │       ├── ConfirmDialog.kt       # 通用确认弹窗
│       │   │       ├── LoadingIndicator.kt
│       │   │       └── TopBar.kt              # 通用顶栏
│       │   ├── data/
│       │   │   ├── api/
│       │   │   │   ├── VideoApi.kt            # Retrofit 接口：视频 CRUD
│       │   │   │   ├── UserApi.kt             # Retrofit 接口：用户认证
│       │   │   │   ├── PlaylistApi.kt         # Retrofit 接口：播放列表
│       │   │   │   ├── CommentApi.kt          # Retrofit 接口：评论
│       │   │   │   ├── WatchApi.kt            # Retrofit 接口：播放/心跳
│       │   │   │   └── YouTubeApi.kt          # Retrofit 接口：YouTube
│       │   │   ├── model/                     # 数据模型（对应后端 JSON 结构）
│       │   │   │   ├── User.kt
│       │   │   │   ├── Video.kt
│       │   │   │   ├── VideoDetail.kt
│       │   │   │   ├── Playlist.kt
│       │   │   │   ├── Comment.kt
│       │   │   │   ├── UploadCredentials.kt
│       │   │   │   └── WatchInfo.kt
│       │   │   └── repository/
│       │   │       ├── UserRepository.kt      # 封装 UserApi，管理 token 持久化
│       │   │       ├── VideoRepository.kt
│       │   │       ├── PlaylistRepository.kt
│       │   │       ├── CommentRepository.kt
│       │   │       └── UploadRepository.kt    # 封装上传全流程
│       │   ├── service/
│       │   │   └── UploadWorker.kt            # WorkManager Worker，后台上传
│       │   └── util/
│       │       ├── TokenManager.kt            # SharedPreferences 存储 token
│       │       └── NetworkUtil.kt
│       └── res/
│           ├── values/
│           │   ├── strings.xml
│           │   └── themes.xml                 # Material 3 主题
│           └── drawable/                      # 图标资源
├── build.gradle.kts                           # 项目级
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
└── gradle/
    └── libs.versions.toml                     # 版本目录统一管理依赖
```

---

## 功能对照表（Web ↔ Android）

| Web 页面 | Android Screen | 功能差异 |
|----------|---------------|---------|
| LoginPage | LoginScreen | 一致：手机号 + 验证码 |
| UploadPage | UploadScreen | Android 增加相机拍摄入口，WorkManager 后台上传 |
| WatchPage | WatchScreen | ExoPlayer 替代 Video.js，增加手势操作（亮度/音量/进度） |
| MyVideosPage | MyVideosScreen | 一致 |
| EditPage | EditScreen | 一致 |
| PlaylistPage | PlaylistScreen + PlaylistDetailScreen | 一致 |
| YouTubePage | YouTubeScreen | 一致 |
| StatisticsPage | **不做** | 移除 |
| CommentSection | CommentSheet (BottomSheet) | 适配移动端交互 |
| NavBar | BottomNavigation | 适配移动端导航 |

---

## 核心流程设计

### 认证

1. 用户输入手机号 → `GET /user/requestVerificationCode?phone=xxx`
2. 输入验证码 → `GET /user/submitVerificationCode?phone=xxx&code=xxx`
3. 返回的 token 存入 `SharedPreferences`（通过 `TokenManager`）
4. OkHttp Interceptor 自动在每个请求 Header 注入 `token: xxx`
5. token 过期或 401 时跳转登录页

### 视频播放

1. 点击视频卡片 → `GET /watchController/getWatchInfo?videoId=xxx`
2. 获取 HLS 地址 → 创建 ExoPlayer + HlsMediaSource
3. ExoPlayer 自动处理自适应码率（480p/720p/1080p）
4. 播放过程中定时调用 `POST /heartbeat/add` 上报进度
5. 再次打开时通过 `GET /progress/getProgress` 恢复播放位置
6. 支持全屏横屏播放、手势调节亮度/音量、双击快进快退

### 视频上传

1. 用户选择相册视频或相机拍摄
2. `POST /video/create` → 获取 videoId
3. `GET /file/getUploadCredentials` → 获取 STS 临时凭证
4. 创建 WorkManager UploadWorker → 通过阿里云 OSS SDK 分片上传
5. 上传进度通过 WorkManager 的 Progress 回调 → 通知栏展示
6. 上传完成 → `GET /file/uploadFinish` → `GET /video/rawFileUploadFinish`
7. App 被杀后 WorkManager 自动恢复上传任务

### 评论

1. 播放页底部显示评论数，点击展开 BottomSheet
2. `GET /comment/getByVideoId?videoId=xxx` 加载评论列表
3. 底部输入框发表评论 → `POST /comment/post`

### 播放列表

1. 底部导航「播放列表」tab → `GET /playlist/getMyPlaylists` 加载列表
2. 点击进入详情 → 展示视频列表，支持长按拖拽排序
3. 新建/编辑/删除播放列表 → 对应 API

---

## 导航结构

```
BottomNavigation:
  [首页]  [播放列表]  [+上传]  [我的视频]  [设置]

路由图:
  /login            → LoginScreen
  /home             → HomeScreen           (BottomNav tab)
  /playlist         → PlaylistScreen       (BottomNav tab)
  /upload           → UploadScreen         (BottomNav 中间 FAB)
  /myvideos         → MyVideosScreen       (BottomNav tab)
  /settings         → SettingsScreen       (BottomNav tab)
  /watch/{videoId}  → WatchScreen          (从列表跳转)
  /edit/{videoId}   → EditScreen           (从我的视频跳转)
  /playlist/{id}    → PlaylistDetailScreen (从播放列表跳转)
  /youtube          → YouTubeScreen        (从设置或首页入口)
```

---

## API 对接

Android 端直接复用后端现有 REST API，无需后端改动。

**Base URL**：通过 `BuildConfig` 配置，区分 debug/release：
- Debug: `http://10.0.2.2:5022`（模拟器本机映射）
- Release: `https://oneclick.video`

**请求头**：OkHttp Interceptor 统一注入
```
token: {saved_token}
clientId: {device_client_id}
sessionId: {current_session_id}
```

---

## 需要更新的文档

| 文档 | 更新内容 |
|------|---------|
| `/README.md` | 新增 Android 构建/运行说明 |
| `/docs/1-关键设计.md` | 新增 Android 客户端架构段落 |
| `/docs/api/7-App接口.md` | 补充 Android 客户端版本检查说明 |

文档风格：结构化、面向 AI 可读、使用表格和代码块、避免散文叙述。

---

## 不做的事

- **统计页**：用户确认不需要
- **离线缓存**：首版不做视频离线下载
- **推送通知**：首版不做转码完成推送
- **深色模式**：首版使用系统默认，不做独立切换
- **多语言**：仅中文
