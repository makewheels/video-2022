# AGENT.md — video-2022

## 项目概述
这是一个全栈视频分享平台。开发任何功能前，请先阅读 `CONTRIBUTING.md` 了解完整开发流程。

## 技术栈
- 后端: Java 21 + Spring Boot 4.0.3 + MongoDB + Aliyun OSS/MPS
- Web: React 19 + TypeScript + Vite
- Android: Kotlin + Jetpack Compose + Hilt
- iOS: Swift 6 + SwiftUI
- CLI: Python 3.12 + Click
- 开发者控制台: React + TypeScript

## 目录约定
- `server/` — 后端（之前叫 backend）
- `web/` — Web 前端（之前叫 frontend）
- `console/` — 开发者控制台（之前叫 developer-portal）
- `android/` / `ios/` / `cli/` / `test/` — 各平台客户端和测试

## 代码规范
- 后端测试继承 `BaseIntegrationTest`，使用真实 MongoDB（test profile）
- 所有外部服务（OSS、MPS）在测试中使用 `@MockitoBean`
- Commit message 格式: `type: 描述`（feat/fix/test/docs/refactor）
- 每个 PR 必须更新 `docs/CHANGELOG.md`
- 密钥通过环境变量注入，不能硬编码

## 重要约束
- 所有部署通过 GitHub Actions 流水线，禁止直接操作服务器
- CI 有 9 个 Job，全部通过才能合并
- 新功能必须有单元测试
- 后端接口变更需同步更新客户端代码
