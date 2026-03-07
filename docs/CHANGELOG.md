# 变更日志

本文档记录项目的所有关键变更，按时间倒序排列。

每个 PR 合并后，在此文件顶部添加记录。

---

### PR #25: 新增视频编辑页面
- **编辑页面**: `/edit.html?videoId=xxx`，独立页面编辑视频标题和描述
- **只读信息展示**: 播放次数、时长、创建时间、视频类型、播放链接（带复制）
- **视频列表**: 操作列增加"编辑"链接

### PR #24: "我的视频"页面改造 + 分页
- **导航栏**: "首页" → "我的视频"（全部6个页面同步）
- **页面简化**: 去掉欢迎区，保留快捷操作卡片
- **视频卡片增强**: 显示播放次数 · 时长 · 创建时间
- **分页（响应式）**: 电脑端传统分页器 / 手机端"加载更多"按钮
- **API改动**: `getMyVideoList` 返回 `{ list: [...], total: N }`
- **新增**: `VideoListVO` 响应类, `VideoRepository.countVideosByUserId()`
- **测试**: 更新 Playwright 测试适配新导航和页面结构

### PR #23: 播放器升级 — Video.js + 分辨率切换 + 键盘快捷键 + 播放统计
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

### [PR #22](https://github.com/makewheels/video-2022/pull/22) — 专家级代码审查 + 测试文档

- 修正 plan 文件日期（2025-07 → 2026-03-07）
- 修复 3 个关键 Bug：RawFileService/VideoService 视频类型字段比较错误、FileController 上传凭证鉴权缺失
- 安全改进：RequestUtil null 检查、GlobalExceptionHandler 日志规范、WatchService 竞态条件修复、验证码 "111" 环境守卫
- 创建测试文档体系：11 篇模块测试文档，覆盖 490 个用例（409 Java + 82 Playwright）

### [PR #21](https://github.com/makewheels/video-2022/pull/21) — 代码审查修复

- 修复 PlaylistRepository 字段名 `isDelete` → `deleted`，与 Playlist 实体一致
- 修复 CheckTokenInterceptor 双重响应（移除多余的 setStatus(403)）
- WatchService 添加 null 安全检查
- 配置文件 URL 改为环境变量 fallback 模式
- 清理 TODO 注释，FileAccessLogService 改用 RequestUtil.getIp()
- 修正 API 文档 getRawFileDownloadUrl 响应格式

## 2026 年 3 月 7 日

### [PR #20](https://github.com/makewheels/video-2022/pull/20) — 文档全面评审与改进

- README 清理：去除与 CHANGELOG 重复的 PR 表格、修复指向归档文档的断链
- 10 篇业务文档添加「源码位置」引用表
- 8 篇 API 文档添加认证说明和错误响应码，标注公开/需认证接口
- 7 个计划文档添加完成状态和对应 PR 链接
- 关键设计文档添加源码包结构图

### [PR #19](https://github.com/makewheels/video-2022/pull/19) — CHANGELOG + 开发规范

- 创建 `docs/CHANGELOG.md`，整合 PR #2-#18 全部变更 + 2022-2025 历史记录
- README 新增「开发规范」：禁止直接 push master、一个功能一个 PR、PR 后更新 CHANGELOG
- README 开发记录表格补全 PR #2-#18

### [PR #18](https://github.com/makewheels/video-2022/pull/18) — 测试套件全面改进

- 删除 10 个无断言的垃圾测试文件
- 新增 23 个测试文件，覆盖 CheckService、财务、视频、文件、通知、转码等全部未测试服务
- 增强现有 E2E 和集成测试：错误场景、边界条件
- 新增 Playwright 行为测试（`behavior.spec.js`）
- 总计：Java 392 测试 + Playwright 261 测试 = 653+ 测试

### [PR #17](https://github.com/makewheels/video-2022/pull/17) — 前端全面优化

- 重新设计导航栏：桌面展开 + 移动端汉堡菜单
- 首页重构：快捷操作卡片 + 视频网格布局
- 响应式 CSS：3 个断点（480px / 768px / 桌面）
- 统一页脚，上传页面美化
- 21 个 Playwright 前端测试

### [PR #16](https://github.com/makewheels/video-2022/pull/16) — E2E 端到端测试

- 新增 BaseE2ETest 基类（真实 HTTP + 真实 MongoDB + 自动清理）
- 5 个 E2E 测试类：登录、视频上传、视频修改、视频播放、播放列表
- 4 个 Playwright 浏览器 E2E 测试

### [PR #15](https://github.com/makewheels/video-2022/pull/15) — UX 优化

- 全局 Toast 提示系统（成功/错误/信息）
- 输入验证与实时反馈
- 空状态提示（无视频、无播放列表）
- 首页快捷入口

### [PR #14](https://github.com/makewheels/video-2022/pull/14) — 前端重新设计

- YouTube 风格界面重设计
- 深色/浅色主题切换
- 全面响应式布局

## 2026 年 3 月 6 日

### [PR #13](https://github.com/makewheels/video-2022/pull/13) — 综合测试套件

- 新增 12 个测试类，130 个测试
- 覆盖用户、视频创建、播放列表、账单、封面等核心服务
- 建立 BaseIntegrationTest 基类

### [PR #12](https://github.com/makewheels/video-2022/pull/12) — 文档目录整理

- 老文档移入 `docs/归档/` 目录
- 保留业务文档和 API 文档在主目录

### [PR #11](https://github.com/makewheels/video-2022/pull/11) — 业务文档

- 添加 10 篇业务流程文档（3,668 行）
- 覆盖：视频上传、转码、播放、存储、YouTube 下载、用户设备、封面截取、计费、播放列表、系统服务

### [PR #10](https://github.com/makewheels/video-2022/pull/10) — Spring Boot 4.x 兼容性 + 文档

- 修复 lettuce-core 升级到 6.8.2
- 添加 `-parameters` 编译参数
- 全面更新 README 和 8 个 API 文档

### [PR #9](https://github.com/makewheels/video-2022/pull/9) — Spring Boot 4.x 启动修复

- 修复 Spring Boot 4.x 启动时的兼容性问题
- TestRestTemplate 移除适配

### [PR #8](https://github.com/makewheels/video-2022/pull/8) — Copilot 配置迁移

- 迁移 AGENT.md 到 `.github/copilot-instructions.md`
- 添加 Copilot Skills 体系

### [PR #7](https://github.com/makewheels/video-2022/pull/7) — Spring Boot 4.0.3 升级

- Spring Boot 3.4.1 → 4.0.3
- Spring Cloud 2024.0.0 适配

### [PR #6](https://github.com/makewheels/video-2022/pull/6) — 清理配置文件

- 删除 CLAUDE.md，统一使用 AGENT.md

### [PR #5](https://github.com/makewheels/video-2022/pull/5) — 密钥管理迁移

- 所有密钥迁移到 `.env` 文件
- 删除 RSA 加密和百度 SMS 相关代码
- 环境变量管理统一化

### [PR #4](https://github.com/makewheels/video-2022/pull/4) — Java 21 支持

- 支持 Java 21 运行环境
- 更新 RSA 私钥文件路径适配 macOS

## 2026 年 3 月 4 日

### [PR #3](https://github.com/makewheels/video-2022/pull/3) — Spring Boot 大版本升级

- Spring Boot 2.7.11 → 3.4.1
- Spring Cloud 2021.0.5 → 2024.0.0
- 迁移到 Jakarta EE 9+（`javax.*` → `jakarta.*`）
- 升级 Lombok 1.18.38、Lettuce Core 6.5.2

### [PR #2](https://github.com/makewheels/video-2022/pull/2) — Lombok 编译修复

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
- 改造钉钉通知类 NotificationService，异常发送到钉钉
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
