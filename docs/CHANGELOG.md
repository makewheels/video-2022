# 变更日志

本文档记录项目的所有关键变更，按时间倒序排列。

每个 PR 合并后，在此文件顶部添加记录。

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
- 限流状态响应头
- 超限返回 429 Too Many Requests

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
- 新增 `CommentServiceTest`（8 个测试用例）

### [PR #35](https://github.com/makewheels/video-2022/pull/35) — 视频点赞/点踩
- 新增 `VideoLike` 实体 + `video_like` MongoDB 集合
- Video 实体新增 `likeCount`、`dislikeCount` 字段
- 新增 `VideoLikeService`：点赞/点踩/取消/切换逻辑，原子计数更新
- 新增 `VideoLikeController`：like、dislike、getStatus API
- watch.html 添加 👍/👎 按钮，显示点赞数（不显示点踩数）
- 新增 `VideoLikeServiceTest`（6 个测试用例）

### [PR #34](https://github.com/makewheels/video-2022/pull/34) — 转码进度展示
- 新增 `GET /video/getVideoStatus` 轻量级状态 API
- upload.html 上传完成后自动轮询转码状态（5s/次，10min 超时）
- 我的视频列表非 READY 视频显示"处理中"标签
- 新增 2 个状态 API 测试用例

### [PR #33](https://github.com/makewheels/video-2022/pull/33) — 视频可见性功能
- Video 实体新增 `visibility` 字段（PUBLIC/UNLISTED/PRIVATE）
- 创建视频时默认 PUBLIC，编辑时可修改
- PRIVATE 视频仅所有者可观看，WatchService 添加权限检查
- upload.html / edit.html 新增可见性下拉框
- 我的视频列表显示可见性图标（🔗 不公开 / 🔒 私密）
- 新增 4 个可见性测试用例

### [PR #32](https://github.com/makewheels/video-2022/pull/32) — 视频搜索功能
- `getMyVideoList` API 新增可选 `keyword` 参数
- MongoDB `$or` + regex 模糊搜索（标题 + 描述），不区分大小写
- 我的视频页面添加搜索输入框 + 搜索/清除按钮
- 新增 4 个搜索测试用例（按标题、按描述、无匹配、大小写不敏感）

### [PR #31](https://github.com/makewheels/video-2022/pull/31) — 视频删除功能
- 新增 `VideoDeleteService`：硬删除视频及所有关联数据（File、Transcode、Cover、PlayItem、WatchLog）
- OSS 文件删除前检查 MD5 引用计数，避免误删共享对象
- 自动从所有播放列表中移除被删除的视频
- `VideoController` 新增 `GET /video/delete` 端点
- edit.html 添加红色"删除视频"按钮 + 确认弹窗
- 我的视频列表每行添加删除操作链接
- 新增 `VideoDeleteServiceTest`（4 个测试用例）

### [PR #30](https://github.com/makewheels/video-2022/pull/30) — 修复测试编译错误
- VideoServiceTest: 适配 PR #24 的 `Result<VideoListVO>` 返回类型变更
- VideoUploadE2ETest: `getMyVideoList` 通过 `data.list` 获取视频列表
- VideoUploadE2ETest: `invalidVideoType` 测试改为非断言式（API 不校验 videoType）

### [PR #29](https://github.com/makewheels/video-2022/pull/29) — 测试文档添加源码链接
- 10 个测试文档中 506 个测试方法名添加 GitHub 源码链接
- 点击方法名可直接跳转到对应测试代码行

### [PR #28](https://github.com/makewheels/video-2022/pull/28) — CHANGELOG 补充时间戳和 PR 链接
- 每个 PR 条目添加合并时间（HH:MM），所有条目补全 GitHub 链接

### [PR #27](https://github.com/makewheels/video-2022/pull/27) — API 成功消息改为英文 `18:45`
- **ErrorCode.SUCCESS**: "成功" → "success"
- **PlaylistController**: 成功消息改为英文（如 "播放列表创建成功" → "playlist created"），错误消息保持中文
- **VideoController**: "视频已创建" → "video created"

### [PR #26](https://github.com/makewheels/video-2022/pull/26) — 统计页面 CDN 优化 `18:36`
- **ECharts CDN**: `cdn.staticfile.org` → `cdn.jsdelivr.net`，统一全站 CDN 源，解决统计页面加载慢的问题

### [PR #25](https://github.com/makewheels/video-2022/pull/25) — 新增视频编辑页面 `18:30`
- **编辑页面**: `/edit.html?videoId=xxx`，独立页面编辑视频标题和描述
- **只读信息展示**: 播放次数、时长、创建时间、视频类型、播放链接（带复制）
- **视频列表**: 操作列增加"编辑"链接

### [PR #24](https://github.com/makewheels/video-2022/pull/24) — "我的视频"页面改造 + 分页 `18:15`
- **导航栏**: "首页" → "我的视频"（全部6个页面同步）
- **页面简化**: 去掉欢迎区，保留快捷操作卡片
- **视频卡片增强**: 显示播放次数 · 时长 · 创建时间
- **分页（响应式）**: 电脑端传统分页器 / 手机端"加载更多"按钮
- **API改动**: `getMyVideoList` 返回 `{ list: [...], total: N }`
- **新增**: `VideoListVO` 响应类, `VideoRepository.countVideosByUserId()`
- **测试**: 更新 Playwright 测试适配新导航和页面结构

### [PR #23](https://github.com/makewheels/video-2022/pull/23) — 播放器升级 — Video.js + 分辨率切换 + 键盘快捷键 + 播放统计 `17:49`
- **播放器引擎**: Aliplayer 2.13.2 → Video.js 8.10.0
- **分辨率切换**: videojs-http-source-selector 插件
- **键盘快捷键**: 空格暂停、方向键进退、F全屏、M静音
- **时间跳转**: `?t=21` 替代 `?seekTimeInMills=`
- **记忆播放**: localStorage + 后端双重存储
- **播放统计**: PlaybackSession 实体（start/heartbeat/exit）
- **退出记录**: sendBeacon 上报
- **心跳间隔**: 2秒 → 15秒
- **Video.js 主题 CSS**: 控件样式、暗色主题适配
- **修复 axios CDN 404**: 全部6个页面统一使用 jsdelivr + 版本锁定 0.26.1
- **upload 页面 8 项 UX 改进**: 文件选择反馈、修改/复制/列表操作 toast 提示、OSS 上传 try/catch、文件大小校验、进度条重置、空列表按钮禁用
- **README 新增前端 UX 规范**: 所有操作必须有 toast 反馈、CDN 统一规则、错误处理要求
- **CDN 可用性 E2E 测试**: 6 个真实测试验证所有页面脚本无 404
- **upload UX Playwright 测试**: 5 个新测试覆盖文件选择、修改提示、空列表禁用等
- **测试**: PlaybackServiceTest (8个) + player.spec.js (8个) + upload UX (5个) + CDN E2E (6个)
- **文件变更**: watch.html, upload.html, global.css, README.md, 7个新Java文件, 多个Playwright文件

### [PR #22](https://github.com/makewheels/video-2022/pull/22) — 专家级代码审查 + 测试文档 `13:42`

- 修正 plan 文件日期（2025-07 → 2026-03-07）
- 修复 3 个关键 Bug：RawFileService/VideoService 视频类型字段比较错误、FileController 上传凭证鉴权缺失
- 安全改进：RequestUtil null 检查、GlobalExceptionHandler 日志规范、WatchService 竞态条件修复、验证码 "111" 环境守卫
- 创建测试文档体系：11 篇模块测试文档，覆盖 490 个用例（409 Java + 82 Playwright）

### [PR #21](https://github.com/makewheels/video-2022/pull/21) — 代码审查修复 `13:12`

- 修复 PlaylistRepository 字段名 `isDelete` → `deleted`，与 Playlist 实体一致
- 修复 CheckTokenInterceptor 双重响应（移除多余的 setStatus(403)）
- WatchService 添加 null 安全检查
- 配置文件 URL 改为环境变量 fallback 模式
- 清理 TODO 注释，FileAccessLogService 改用 RequestUtil.getIp()
- 修正 API 文档 getRawFileDownloadUrl 响应格式

## 2026 年 3 月 7 日

### [PR #20](https://github.com/makewheels/video-2022/pull/20) — 文档全面评审与改进 `13:00`

- README 清理：去除与 CHANGELOG 重复的 PR 表格、修复指向归档文档的断链
- 10 篇业务文档添加「源码位置」引用表
- 8 篇 API 文档添加认证说明和错误响应码，标注公开/需认证接口
- 7 个计划文档添加完成状态和对应 PR 链接
- 关键设计文档添加源码包结构图

### [PR #19](https://github.com/makewheels/video-2022/pull/19) — CHANGELOG + 开发规范 `12:43`

- 创建 `docs/CHANGELOG.md`，整合 PR #2-#18 全部变更 + 2022-2025 历史记录
- README 新增「开发规范」：禁止直接 push master、一个功能一个 PR、PR 后更新 CHANGELOG

### [PR #18](https://github.com/makewheels/video-2022/pull/18) — 测试套件全面改进 `12:34`

- 删除 10 个无断言的垃圾测试文件
- 新增 23 个测试文件，覆盖 CheckService、财务、视频、文件、通知、转码等全部未测试服务
- 增强现有 E2E 和集成测试：错误场景、边界条件
- 新增 Playwright 行为测试（`behavior.spec.js`）
- 总计：Java 392 测试 + Playwright 261 测试 = 653+ 测试

### [PR #17](https://github.com/makewheels/video-2022/pull/17) — 前端全面优化 `10:46`

- 重新设计导航栏：桌面展开 + 移动端汉堡菜单
- 首页重构：快捷操作卡片 + 视频网格布局
- 响应式 CSS：3 个断点（480px / 768px / 桌面）
- 统一页脚，上传页面美化
- 21 个 Playwright 前端测试

### [PR #16](https://github.com/makewheels/video-2022/pull/16) — E2E 端到端测试 `10:07`

- 新增 BaseE2ETest 基类（真实 HTTP + 真实 MongoDB + 自动清理）
- 5 个 E2E 测试类：登录、视频上传、视频修改、视频播放、播放列表
- 4 个 Playwright 浏览器 E2E 测试

### [PR #15](https://github.com/makewheels/video-2022/pull/15) — UX 优化 `09:27`

- 全局 Toast 提示系统（成功/错误/信息）
- 输入验证与实时反馈
- 空状态提示（无视频、无播放列表）
- 首页快捷入口

### [PR #14](https://github.com/makewheels/video-2022/pull/14) — 前端重新设计 `09:04`

- YouTube 风格界面重设计
- 深色/浅色主题切换
- 全面响应式布局

## 2026 年 3 月 6 日

### [PR #13](https://github.com/makewheels/video-2022/pull/13) — 综合测试套件 `01:12`

- 新增 12 个测试类，130 个测试
- 覆盖用户、视频创建、播放列表、账单、封面等核心服务
- 建立 BaseIntegrationTest 基类

### [PR #12](https://github.com/makewheels/video-2022/pull/12) — 文档目录整理 `00:03`

- 老文档移入 `docs/归档/` 目录
- 保留业务文档和 API 文档在主目录

### [PR #11](https://github.com/makewheels/video-2022/pull/11) — 业务文档 `00:00`

- 添加 10 篇业务流程文档（3,668 行）
- 覆盖：视频上传、转码、播放、存储、YouTube 下载、用户设备、封面截取、计费、播放列表、系统服务

### [PR #10](https://github.com/makewheels/video-2022/pull/10) — Spring Boot 4.x 兼容性 + 文档 `23:33`

- 修复 lettuce-core 升级到 6.8.2
- 添加 `-parameters` 编译参数
- 全面更新 README 和 8 个 API 文档

### [PR #9](https://github.com/makewheels/video-2022/pull/9) — Spring Boot 4.x 启动修复 `23:10`

- 修复 Spring Boot 4.x 启动时的兼容性问题
- TestRestTemplate 移除适配

### [PR #8](https://github.com/makewheels/video-2022/pull/8) — Copilot 配置迁移 `22:37`

- 迁移 AGENT.md 到 `.github/copilot-instructions.md`
- 添加 Copilot Skills 体系

### [PR #7](https://github.com/makewheels/video-2022/pull/7) — Spring Boot 4.0.3 升级 `22:16`

- Spring Boot 3.4.1 → 4.0.3
- Spring Cloud 2024.0.0 适配

### [PR #6](https://github.com/makewheels/video-2022/pull/6) — 清理配置文件 `22:15`

- 删除 CLAUDE.md，统一使用 AGENT.md

### [PR #5](https://github.com/makewheels/video-2022/pull/5) — 密钥管理迁移 `22:01`

- 所有密钥迁移到 `.env` 文件
- 删除 RSA 加密和百度 SMS 相关代码
- 环境变量管理统一化

### [PR #4](https://github.com/makewheels/video-2022/pull/4) — Java 21 支持 `20:38`

- 支持 Java 21 运行环境
- 更新 RSA 私钥文件路径适配 macOS

## 2026 年 3 月 4 日

### [PR #3](https://github.com/makewheels/video-2022/pull/3) — Spring Boot 大版本升级 `22:03`

- Spring Boot 2.7.11 → 3.4.1
- Spring Cloud 2021.0.5 → 2024.0.0
- 迁移到 Jakarta EE 9+（`javax.*` → `jakarta.*`）
- 升级 Lombok 1.18.38、Lettuce Core 6.5.2

### [PR #2](https://github.com/makewheels/video-2022/pull/2) — Lombok 编译修复 `20:58`

- 修复 Lombok 与 Java 21 的编译兼容性问题

---

## 历史变更（2022 - 2025）

### 2024 年 6 月
- 新增 GPU 云函数转码支持
- 优化转码服务工厂模式

### 2024 年 3 月
- 新增观看次数统计
- 新增 Client/Session 机制追踪未登录用户

### 2024 年 2 月
- 新增视频封面自动截取功能
- 新增文件访问日志功能

### 2024 年 1 月
- 新增 YouTube 视频下载功能
- 新增播放列表功能
- 新增钱包和账单功能

### 2023 年 12 月
- 加入视频播放记忆进度功能
- 接入保存对象存储访问日志

### 2023 年 5 月
- 把 video 对象抽出 MediaInfo 子类
- 抽出 EnvironmentService，取消 @Value 注入
- 改造登录拦截器
- 新增请求日志拦截器 RequestLogInterceptor

### 2023 年 1 月
- 引入 GlobalExceptionHandler，自定义异常 VideoException
- 引入 cacheService，解决循环依赖

### 2022 年 12 月
- 重新接入阿里云 web 播放器
- 加入自适应码率
- RequestUtil to DTO 改造
- 接入 UserHolder，取消 request 获取 user 对象

### 2022 年 11 月
- RSA 密码加密计划
