# AGENT.md — video-2022

## 项目概述
这是一个全栈视频分享平台。开始改代码前，先读 `README.md` 和 `CONTRIBUTING.md`，再进入对应模块文档。

## 文档地图

### 建议阅读顺序
1. `README.md`
   - 项目概览、运行方式、当前目录结构
   - 顶部自带“文档地图”，是总入口
2. `CONTRIBUTING.md`
   - 分支、PR、测试、文档更新规范
3. `docs/1-关键设计.md`
   - 系统架构总览和业务文档导航
4. 按改动范围继续读
   - 接口契约：`docs/api/*.md`
   - 业务流程：`docs/业务/*.md`
   - 测试体系：`docs/测试/README.md` 和 `docs/测试/*.md`
5. `docs/CHANGELOG.md`
   - 查看最近变更，避免重复实现

### 常用文档入口
- `docs/api/1-用户接口.md` 到 `docs/api/10-点赞接口.md`：后端接口说明
- `docs/业务/1-视频上传与去重.md` 到 `docs/业务/13-视频互动.md`：核心业务实现
- `docs/功能分析报告.md`：当前完成度和待完善项
- `docs/plans/`：历史设计/计划文档，仅作背景参考
- `docs/归档/`：历史资料，默认只读，不作为新增功能的主要依据

### 目录名说明
- 当前有效目录名只有 `server/`、`web/`、`console/`
- 不要再使用旧目录名 `backend/`、`frontend/`、`developer-portal/` 描述当前结构
- 历史文档里如果出现这些旧名字，按“历史路径”理解，不要照着当前代码去找

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
- `android/` / `ios/`：移动端
- `cli/`：Python CLI
- `test/`：Python API / 浏览器端到端测试

## 代码规范
- 后端测试继承 `BaseIntegrationTest`，使用真实 MongoDB（test profile）
- 所有外部服务（OSS、MPS、YouTube、云函数）在测试中使用 `@MockitoBean`
- Commit message 格式：`type: 描述`（feat/fix/test/docs/refactor）
- 每个 PR 必须更新 `docs/CHANGELOG.md`
- 密钥通过环境变量注入，不能硬编码

## 工作方式
- 改接口前，先读对应 `docs/api/*.md` 和业务文档
- 改 CLI 前，先读 `cli/README.md`、相关命令文件和 `cli/tests/`
- 改测试时，优先复用现有测试目录结构，不新造平行体系
- 改文档时，优先更新现行文档；除非明确需要，不修改 `docs/归档/`
- 后端接口、前端调用、CLI 包装改动要一起核对，避免跨端契约漂移
- 任何对外行为变更都要同步更新文档，至少覆盖 `docs/api/`、`docs/业务/`、`docs/CHANGELOG.md`

## 重要约束
- 所有部署通过 GitHub Actions 流水线，禁止直接操作服务器
- CI 有 9 个 Job，全部通过才能合并
- 新功能必须有单元测试
- 后端接口变更需同步更新客户端代码

## 提交与验证
- Commit message 用 `type: 描述`，如 `fix: 收紧播放签名校验`
- 合并前至少跑与改动直接相关的测试和构建
- 部署只能走 GitHub Actions，禁止直接改线上机器
