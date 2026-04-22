# 变更日志

本文档记录项目的所有关键变更，按时间倒序排列。

每个 PR 合并后，在此文件顶部添加记录。

---

## fix: 播放链路与开发者控制台收口
- Web 播放器对齐后端播放会话协议，补上 `playbackSessionId`、退出上报和 `sendBeacon` 退出
- `/file/access` 改为校验 HMAC-SHA256 签名和时间窗口，不再只透传 sign 参数
- 全局异常响应不再向客户端暴露 stack trace，详细堆栈仍保存在异常日志中
- 开发者控制台统一到当前 OAuth 应用模型，补齐 `/developer/stats` 和基于开发者 JWT 的 Webhook 管理 API
- Console 的 Stats / Webhooks / Apps 页面已接通真实后端统计与配置数据
- 补充播放签名、文件访问、开发者统计相关回归测试
- 更新播放、系统服务、测试总览和 AGENT 文档地图

---

## improve: CLI 可靠性与配置体验
- CLI 网络错误、HTTP 错误、无效 JSON 响应统一输出 JSON 错误，不再直接抛 Python traceback
- 新增 `config` 命令组：`show`、`set-base-url`、`set-token`、`clear-token`、`clear`
- `auth logout` 改为仅清除保存的 token，保留其他本地配置
- `video create` 默认类型修正为 `USER_UPLOAD`，兼容旧别名 `UPLOAD`
- `video update` 支持 `UNLISTED` 可见性
- `watch` 命令对齐当前播放会话 API：`start` 支持 `video-id`，`heartbeat` 使用 `playbackSessionId/currentTimeMs`，新增 `watch exit`
- `stats aggregate --output table` 现在支持后端真实的 ECharts 结构响应
- 更新 `AGENT.md`：补充文档地图，移除旧目录名表述，统一到当前项目结构
- CLI 单元测试更新为 118 个通过

---

## feat: 视频分享链接
- 短链接生成与重定向
- 分享统计追踪 (点击量)
- 前端: 分享按钮 + 复制链接 + 社交分享
- 移动端: 系统分享弹窗
- CLI: share create/stats 命令

---

## feat: 观看历史页面
- 观看历史分页查询 (按时间倒序)
- 清除观看历史功能
- 前端: WatchHistoryPage
- 移动端: Android/iOS 观看历史页面
- CLI: watch history/clear-history 命令

---

## feat: 站内通知系统
- 通知实体与 MongoDB 存储
- 通知类型: 评论回复、新订阅、视频点赞、评论点赞
- 通知列表分页查询
- 标记已读/全部已读
- 未读数量 API
- 前端: 通知铃铛图标 + 未读数 badge + 通知列表
- 移动端: 通知页面 (Android + iOS)
- CLI: notification list/read/read-all 命令
- Open API v1: GET /api/v1/notifications

---

## feat: Webhook JWT 管理 API
- 开发者应用注册与管理 (CRUD)
- JWT 令牌签发与验证（24小时过期）
- Webhook 配置与事件推送
- HMAC-SHA256 签名验证
- CLI 开发者工具命令
- 控制台应用管理和 Webhook 配置页面

---

## feat: API 令牌桶限流
- 令牌桶限流算法（默认 60次/分钟，10000次/天）
- 支持按应用自定义配额
- IP 级别兜底限流（未认证请求）
- 限流状态响应头：X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset
- 超限返回 429 Too Many Requests
- RateLimitRecord MongoDB 持久化限流状态
- RateLimitFilter 拦截 /api/v1/** 请求
- CLI: `developer rate-limit-status` 命令

---

## refactor: 迁移 Copilot 指令到 AGENT.md
- 将 `.github/copilot-instructions.md` 迁移到项目根目录 `AGENT.md`
- 统一 AI 助手配置文件位置

---

## fix: 镜像仓库清理与安全加固
- 部署脚本新增远程镜像仓库旧标签自动清理（仅保留最新 1 个）
- 修复文档中服务器 IP 硬编码暴露问题
- 修正 deploy.sh 环境变量模板匹配 application-prod.properties 配置
- 镜像仓库从 `b4/docker-repo` 迁移到 `b4/video-2022`
- 镜像标签简化为时间戳格式（如 `20260316-120401-0022`）

### [PR #82](https://github.com/makewheels/video-2022/pull/82) — 视频搜索
- 新增视频搜索功能，支持标题、描述、标签搜索
- MongoDB 正则匹配搜索方案
- 支持按分类筛选
- 前端搜索页面和搜索栏组件
- Android/iOS 搜索页面
- CLI search 命令

### [PR #80](https://github.com/makewheels/video-2022/pull/80) — 视频标签与分类系统
- Video 实体新增 `tags`（用户自定义标签）和 `category`（预设分类）字段
- 新增 `VideoCategory` 常量类：15 种预设分类（音乐、游戏、教育、科技等）
- 后端：VideoService、VideoRepository 支持标签/分类的增删改查与搜索
- 新增 `CategoryController` 提供分类列表接口
- API v1：UpdateVideoApiRequest 支持 tags/category 字段
- Web 前端：UploadPage、EditPage 新增分类下拉框和标签输入（chip 样式）
- Android：UploadScreen、EditScreen 新增分类选择器和标签芯片输入
- iOS：UploadScreen、EditScreen 新增分类 Picker 和标签输入
- 新增 VideoCategoryTest（5 个测试）和 VideoServiceTest 标签/分类测试（8 个测试）

### [PR #81](https://github.com/makewheels/video-2022/pull/81) — 评论列表分页
- 后端 `GET /comment/getByVideoId` 改为分页接口，支持 `page`、`pageSize`、`sortBy` 参数
- 新增 `CommentPageVO` 返回分页元数据（total、totalPages、currentPage、pageSize）
- API v1 `GET /api/v1/videos/{videoId}/comments` 同步支持分页
- Web: `CommentSection` 使用分页元数据，按钮式"加载更多"
- Android: `CommentViewModel` / `CommentRepository` 基于分页元数据加载，底部自动加载更多
- iOS: `CommentSheet` 支持分页加载，新增"加载更多"按钮
- 新增 7 个分页相关测试（元数据校验、末页、空页、超页、排序）

### [PR #79](https://github.com/makewheels/video-2022/pull/79) — Docker 镜像标签格式优化
- 镜像标签改为北京时间 + 流水线序号格式: `video-2022-20260315-175000-0042`
- 替代之前的 git commit hash 格式，更直观易读

### [PR #78](https://github.com/makewheels/video-2022/pull/78) — 功能分析与改进建议
- 新增 `docs/功能分析报告.md`：功能完成度、代码质量发现、UX 测试结果、改进建议

### [PR #77](https://github.com/makewheels/video-2022/pull/77) — 多端测试补充
- Android: 新增 5 个 ViewModel 测试 (Comment, Watch, Upload, Settings, MyVideos) — 24 个测试
- iOS: 新增 APIClientTests — 10 个测试
- Web: 新增 WatchPage, UploadPage, YouTubePage 测试 — 11 个测试
- 全平台测试: Android 101, iOS 61, Web 36

### [PR #76](https://github.com/makewheels/video-2022/pull/76) — 控制台页面实现
- Stats 页面：应用总数统计、应用列表详情
- Webhooks 页面：应用选择器、Webhook 配置表单、事件类型勾选、列表管理
- API Client 新增 webhook 管理方法
- 新增 CSS 样式（alert-warning, webhook-select, webhook-active）

### [PR #75](https://github.com/makewheels/video-2022/pull/75) — Webhook 事件派发完善
- Video 实体新增 `apiAppId` 字段，API 创建视频时自动关联 OAuthApp
- WebhookEventPublisher 完整实现：查找 apiAppId，调用 WebhookDispatchService 派发
- 集成调用：VideoReadyService、TranscodeCallbackService、VideoDeleteService
- 新增 WebhookEventPublisherTest: 11 个单元测试
- 477 个后端测试全部通过

### [PR #74](https://github.com/makewheels/video-2022/pull/74) — Docker 部署迁移
- 新增 `Dockerfile` — 多阶段构建 (Node→Maven→JRE)
- 新增 `.dockerignore` — 优化构建上下文
- 重构 `deploy.yml` — CI 构建镜像并推送阿里云容器服务，控制台单独构建部署
- 重构 `deploy.sh` — Docker pull + run 模式，自动安装 Docker
- 新增 GitHub Secrets: DOCKER_REGISTRY_USERNAME, DOCKER_REGISTRY_PASSWORD
- 更新 CONTRIBUTING.md 部署章节

### [PR #72](https://github.com/makewheels/video-2022/pull/72) — 重命名项目目录结构
- `backend/` → `server/`（后端服务）
- `frontend/` → `web/`（Web 前端）
- `developer-portal/` → `console/`（开发者控制台）
- 更新所有 CI/CD 工作流、部署脚本、Nginx 配置、React Router basename
- 更新 README.md、CONTRIBUTING.md、.gitignore、所有文档
- 467 个后端测试全部通过
### [PR #73](https://github.com/makewheels/video-2022/pull/73) — 新增 AI 友好文件和功能分析
- 新增 `llms.txt` — 遵循 llms.txt 标准，为 LLM 提供项目结构化信息
- 新增 `.github/copilot-instructions.md` — GitHub Copilot 开发指引
- 更新 CHANGELOG.md

### [PR #68](https://github.com/makewheels/video-2022/pull/68) — 添加应用版本检查功能的测试
- 后端 `AppServiceTest`: 10个集成测试（checkUpdate 7场景 + publishVersion 3场景）
- Android `UpdateViewModelTest`: 7个单元测试（版本检查、强制更新、dismiss、API错误）
- iOS `UpdateCheckManagerTests`: 12个单元测试（版本检查、强制更新、网络错误、JSON解码）
- 全部测试通过：后端451、Android 13、iOS 51
### [PR #69](https://github.com/makewheels/video-2022/pull/69) — 移除钉钉通知功能
- 删除 `etc/ding/` 包下全部 5 个类（DingService、NotificationService、RobotFactory、RobotConfig、RobotType）
- 删除 DingApi 模型类
- 清理调用方：GlobalExceptionHandler、VideoReadyService、WatchService 移除钉钉推送
- 移除 4 个 properties 文件中的 `dingtalk.*` 配置
- 移除 pom.xml 中 `alibaba-dingtalk-service-sdk` 依赖
- 移除测试中的 @MockitoBean
- 更新 10-系统服务.md 等文档
- 428 个后端测试全部通过

### [PR #68](https://github.com/makewheels/video-2022/pull/68) — 添加应用版本检查功能的测试
- 后端 AppServiceTest：10 个测试（checkUpdate 各场景、强制更新、publishVersion）
- Android UpdateViewModelTest：7 个测试（对话框显示/隐藏、API 错误、字段验证）
- iOS UpdateCheckManagerTests：12 个测试（更新检测、JSON 解码、状态管理）
- 测试结果：后端 451 ✅、Android BUILD SUCCESSFUL ✅、iOS 51 ✅

### [PR #67](https://github.com/makewheels/video-2022/pull/67) — API文档示例 + OpenAPI控制器单元测试
- 15 个 DTO 文件（56 个字段）添加 `@Schema` 注解（中文描述和示例值）
- DeveloperControllerTest：14 个测试（注册、登录、JWT 验证、应用管理）
- OAuthControllerTest：15 个测试（令牌签发、刷新、吊销、验证）
- 全部 470 个测试通过（441 + 29 新增）
### [PR #71](https://github.com/makewheels/video-2022/pull/71) — 开发流程规范文档
- 新增 `CONTRIBUTING.md` — 完整的开发流程规范
  - 开发前准备（必须先阅读文档）
  - 分支和 PR 规范
  - 多端同步修改影响矩阵
  - 单元测试要求和模式
  - OpenAPI 接口修改规范
  - 部署影响评估
  - 文档更新规范
  - 代码审查清单
- 更新 README.md 引用 CONTRIBUTING.md

### [PR #44](https://github.com/makewheels/video-2022/pull/44) — 项目重构：前后端分离
- 项目结构拆分为 `frontend/`、`backend/`、`test/` 三子项目
- 新建 React + Vite + TypeScript 前端：8 个页面、11 个组件、14 个前端测试
- 后端迁入 `backend/`，新增 `SpaController` 支持 SPA 路由
- 删除旧版 HTML/JS 前端文件和 Thymeleaf 模板
- Python E2E 测试：24 个 API 测试 + 49 个浏览器测试（替代 Java E2E + JS Playwright）
- CI 从 2 Job 扩展为 4 Job：后端测试、前端测试、API E2E、浏览器 E2E

### [PR #43](https://github.com/makewheels/video-2022/pull/43) — 文档补全与命名修正
- 新增 `docs/业务/11-视频删除与级联.md`、`12-评论与回复系统.md`、`13-视频互动.md`
- 修正测试计数：README 510→562、视频模块 105→101、播放与统计 36→39
- plan 文件名加入时分秒时间戳，修正 brainstorming skill 命名格式

### [PR #42](https://github.com/makewheels/video-2022/pull/42) — 文档补充
- 新增 `docs/api/9-评论接口.md`：评论系统 6 个接口完整文档
- 新增 `docs/api/10-点赞接口.md`：点赞系统 3 个接口完整文档
- 更新 `docs/api/2-上传视频接口.md`：补充删除、状态查询、关键词搜索、可见性字段
- 更新 `docs/测试/2-视频模块测试.md`：测试用例 82→105

### [PR #41](https://github.com/makewheels/video-2022/pull/41) — 测试覆盖补充
- 新增 23 个测试用例（450→473），覆盖评论授权、级联删除、回复链、分页排序、多用户点赞、私密视频访问控制

### [PR #40](https://github.com/makewheels/video-2022/pull/40) — 代码审查修复
- 修复 CRITICAL：`CommentService.deleteComment()` 先查询回复再删除，避免遗留 CommentLike
- 新增：`VideoDeleteService` 级联删除评论、评论点赞、视频点赞
- 更新：CI 设计文档反映合并后的单 workflow 结构

### [PR #38](https://github.com/makewheels/video-2022/pull/38) — GitHub Actions CI/CD
- 新增 `ci.yml`：集成测试自动化（PR + push to master 触发）
- 新增 `e2e.yml`：E2E 测试自动化（需配置阿里云 Secrets）
- Service Containers：MongoDB 7 + Redis 7

### [PR #39](https://github.com/makewheels/video-2022/pull/39) — CI 优化
- 合并 ci.yml 和 e2e.yml 为单个 workflow，两个 job 并行执行
- 配置 7 个阿里云 GitHub Secrets
- 集成测试无需 secrets（外部服务全部 mock）

### [PR #37](https://github.com/makewheels/video-2022/pull/37) — 分享按钮
- watch.html 添加 🔗 分享按钮（点赞按钮旁）
- 点击复制当前页面链接到剪贴板，toast 提示
- 支持 Clipboard API + fallback execCommand

### [PR #36](https://github.com/makewheels/video-2022/pull/36) — 评论系统
- 新增 `Comment` 实体 + `comment` MongoDB 集合
- 新增 `CommentLike` 实体 + `comment_like` 集合（commentId+userId 唯一索引）
- Video 实体新增 `commentCount` 字段
- 新增 `CommentService`：添加评论/回复、分页查询、删除（级联）、点赞 toggle、评论数
- 新增 `CommentController`：add、getByVideoId、getReplies、delete、like、getCount API
- watch.html 添加评论区：输入框、评论列表、回复展开、排序切换、加载更多
- 两级评论结构（顶级评论 + 回复），YouTube 风格
