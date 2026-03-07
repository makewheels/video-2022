# E2E 端到端测试设计

## 目标

建立真实的端到端测试体系，验证视频平台核心业务流程在真实环境中的完整性。
不 mock 任何外部服务，使用本地 MongoDB 和阿里云 OSS 进行真实数据验证。

## 架构

### 两层测试体系

**第一层：Java HTTP E2E 测试**
- `BaseE2ETest` 基类，不 mock 外部服务
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`
- dev 配置：`video-2022` 数据库 + 真实 OSS（video-2022-dev）
- HTTP 接口级别调用
- 测试后自动清理 MongoDB 和 OSS 数据

**第二层：Playwright 浏览器 E2E 测试**
- 操控真实 Chromium 浏览器
- 模拟用户在页面上的操作
- 验证 UI 展示和交互行为
- 对接本地运行的服务器

### 认证方式
- 调用 `/user/requestVerificationCode?phone=测试手机号`
- 调用 `/user/submitVerificationCode?phone=测试手机号&code=111`（dev 模式 111 万能验证码）
- 提取 token，后续请求通过 header 或 query param 传递

### 清理策略
- `@AfterEach` 收集测试创建的所有资源 ID
- 删除 MongoDB 记录（Video、File、Playlist、PlayItem、User 等）
- 删除 OSS 上对应的文件对象
- 保证测试幂等

## 测试场景

### 场景 1：登录注册流程
**Java E2E:**
- 请求验证码 → 提交验证码登录 → 返回 User + token
- 验证 MongoDB 中 User 和 Session 记录
- 用 token 访问受保护接口成功

**Playwright E2E:**
- 浏览器打开登录页
- 输入手机号 → 发送验证码 → 输入验证码 → 登录
- 验证跳转到目标页面
- 验证 localStorage 中有 token

### 场景 2：视频创建上传全流程
**Java E2E:**
- 登录获取 token
- POST /video/create → 返回 videoId + fileId
- GET /file/getUploadCredentials → 获取 STS 临时凭证
- 使用凭证上传测试文件到 OSS
- GET /file/uploadFinish → 标记上传完成
- 验证 OSS 中文件存在
- 验证 MongoDB 中 Video 和 File 状态正确

**Playwright E2E:**
- 浏览器登录后打开上传页
- 选择文件上传
- 观察上传进度
- 刷新页面，验证视频出现在列表中

### 场景 3：视频信息修改
**Java E2E:**
- 创建并上传视频
- POST /video/updateInfo → 修改 title 和 description
- GET /video/getVideoDetail → 验证返回新值
- 直接查询 MongoDB 验证数据已更新

**Playwright E2E:**
- 打开上传页看到视频
- 点击修改按钮
- 修改标题和描述 → 保存
- 刷新页面 → 验证显示新标题

### 场景 4：视频播放验证
**Java E2E:**
- 创建已上传的视频（手动设置 READY 状态）
- GET /watchController/getWatchInfo → 验证返回视频信息
- 验证 watchCount 递增
- 验证 WatchLog 记录创建

**Playwright E2E:**
- 打开播放页面
- 验证视频标题显示
- 验证播放器组件加载

### 场景 5：播放列表管理
**Java E2E:**
- 创建多个视频
- POST /playlist/addPlaylistItem → 添加到列表
- GET /playlist/getMyPlaylistByPage → 验证列表包含视频
- 验证 MongoDB 中 Playlist 和 PlayItem 记录

**Playwright E2E:**
- 上传页面查看列表
- 验证视频出现在列表中
- 列表链接可点击

## 技术细节

### 测试视频文件
使用 FFmpeg 生成一个小的测试视频（约 100KB），避免上传大文件。

### OSS 上传
使用阿里云 OSS SDK 直接上传（和前端客户端上传逻辑一致）。

### 数据库
直接使用 `video-2022` 本地开发数据库，不使用 `video-2022-test`。

### Bug 修复
- 调查并修复手机号显示为 `****` 的问题
