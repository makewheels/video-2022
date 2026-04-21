# AGENT.md — video-2022

## 项目概述
这是一个全栈视频分享平台。开发任何功能前，请先阅读 `CONTRIBUTING.md` 了解完整开发流程。

## 文档地图
开始任何改动前，优先按这个顺序建立上下文：

1. `README.md`
   - 项目概览、运行方式、当前目录结构
   - 顶部自带“文档地图”，是总入口
2. `CONTRIBUTING.md`
   - 分支、PR、测试、文档更新规范
3. `docs/1-关键设计.md`
   - 系统架构总览和业务文档导航
4. 相关领域文档
   - API：`docs/api/*.md`
   - 业务：`docs/业务/*.md`
   - 测试：`docs/测试/README.md` 和 `docs/测试/*.md`
5. `docs/CHANGELOG.md`
   - 查看最近变更，避免重复实现

历史文档在 `docs/归档/`，默认只读，不作为新增功能的主要依据。

## 技术栈
- 后端: Java 21 + Spring Boot 4.0.3 + MongoDB + Aliyun OSS/MPS
- Web: React 19 + TypeScript + Vite
- Android: Kotlin + Jetpack Compose + Hilt
- iOS: Swift 6 + SwiftUI
- CLI: Python 3.12 + Click
- 开发者控制台: React + TypeScript

## 目录约定
- `server/` — 后端
- `web/` — Web 前端
- `console/` — 开发者控制台
- `android/` / `ios/` / `cli/` / `test/` — 各平台客户端和测试

不要再使用旧目录名 `backend/`、`frontend/`、`developer-portal/` 描述当前结构。

## 代码规范
- 后端测试继承 `BaseIntegrationTest`，使用真实 MongoDB（test profile）
- 所有外部服务（OSS、MPS）在测试中使用 `@MockitoBean`
- Commit message 格式: `type: 描述`（feat/fix/test/docs/refactor）
- 每个 PR 必须更新 `docs/CHANGELOG.md`
- 密钥通过环境变量注入，不能硬编码

## 工作方式
- 改接口前，先读对应 `docs/api/*.md` 和业务文档
- 改 CLI 前，先读 `cli/README.md`、相关命令文件和 `cli/tests/`
- 改测试时，优先复用现有测试目录结构，不新造平行体系
- 改文档时，优先更新现行文档；除非明确需要，不修改 `docs/归档/`

## 重要约束
- 所有部署通过 GitHub Actions 流水线，禁止直接操作服务器
- CI 有 9 个 Job，全部通过才能合并
- 新功能必须有单元测试
- 后端接口变更需同步更新客户端代码
