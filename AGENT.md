# AGENT.md — video-2022

## 项目概述
这是一个全栈视频分享平台。开始改代码前，先读 `README.md` 和 `CONTRIBUTING.md`，再进入对应模块文档。

## 文档地图

### 建议阅读顺序
1. `README.md` — 项目概览、目录结构、快速开始
2. `CONTRIBUTING.md` — 分支、提交、测试、PR 规范
3. `docs/1-关键设计.md` — 业务文档和 API 文档总入口
4. 按改动范围继续读：
   - 接口契约：`docs/api/*.md`
   - 业务流程：`docs/业务/*.md`
   - 测试体系：`docs/测试/README.md`
   - 历史变更：`docs/CHANGELOG.md`

### 常用文档入口
- `docs/api/1-用户接口.md` 到 `docs/api/10-点赞接口.md`：后端接口说明
- `docs/业务/1-视频上传与去重.md` 到 `docs/业务/13-视频互动.md`：核心业务实现
- `docs/功能分析报告.md`：当前完成度和待完善项
- `docs/plans/`：历史设计/计划文档，仅作背景参考

### 目录名说明
- 当前有效目录名只有 `server/`、`web/`、`console/`
- 历史文档里如果出现 `backend/`、`frontend/`、`developer-portal/`，那是旧名字，不再用于当前代码路径

## 技术栈
- 后端：Java 21 + Spring Boot 4.0.3 + MongoDB
- Web：React 19 + TypeScript + Vite
- Console：React + TypeScript + TanStack Query
- Android：Kotlin + Jetpack Compose
- iOS：Swift 6 + SwiftUI
- CLI：Python 3.12 + Click
- 测试：Maven + pytest + Playwright + Vitest

## 目录约定
- `server/`：Java 后端，`video/` 是主模块，`youtube/` 是子模块
- `web/`：用户前台 React SPA
- `console/`：开发者控制台
- `android/`、`ios/`：移动端
- `cli/`：Python CLI
- `test/`：Python API / 浏览器端到端测试

## 开发约束
- 后端测试继承 `BaseIntegrationTest`，真实连接 test profile 下的 MongoDB
- 外部服务（OSS、MPS、YouTube、云函数）在测试里通过 `@MockitoBean` 模拟
- 后端接口、前端调用、CLI 包装改动要一起核对，避免跨端契约漂移
- 任何对外行为变更都要同步更新文档，至少覆盖 `docs/api/`、`docs/业务/`、`docs/CHANGELOG.md`
- 密钥必须走环境变量，不能硬编码在源码里

## 提交与验证
- Commit message 用 `type: 描述`，如 `fix: 收紧播放签名校验`
- 每个 PR 都要更新 `docs/CHANGELOG.md`
- 合并前至少跑与改动直接相关的测试和构建
- 部署只能走 GitHub Actions，禁止直接改线上机器
