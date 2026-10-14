# CI 警告清理（2026-10-14）

基线：[38517659481](https://github.com/makewheels/video-2022/actions/runs/38517659481)。该运行成功不代表没有警告。此次处理用户前一轮明确列出的旧 Action 和 12 条前端 lint 警告，不声称全仓库所有日志已无警告。

## 本阶段修复

- `actions/upload-artifact` v5 → v6（CI、deploy、release）。
- `actions/download-artifact` v5 → v7（CI、deploy）。
- `actions/cache` v4 → v5（CI）。
- 查询上述版本 action.yml，均实际声明 node24；不能只按主版本数字推断，download-artifact v6 仍声明 node20。
- 12 条 lint 涉及 AppsPage/WebhooksPage、WatchHistoryPage/SettingsPage/ChatPage、NavBar/VideoPlayer；通过拆分用例状态、局部视图和事件处理消除过长/复杂度，补齐 Effect/Callback 依赖，不放宽或禁用规则。
- 两端 CI lint 加 `--max-warnings 0`。播放跳转、上传、API 和用户用法没有新增需求变更。
- 既有 MyVideosPage/ChatPage 测试等待真实异步状态后断言，不截断 console 输出。

## 实际验证

- 本地全量 Web 与 console ESLint 均 0 errors / 0 warnings。
- 本地 Web 14 文件、51 项测试通过；包含新增播放器左右键/j/l、输入与未知键、逐字节 UTF-8/多行 SSE、工具结果和确认消息回归。
- 本地两端 TypeScript/Vite 构建通过。安装、缓存、构建产物均在系统临时目录，验证后清理，未在项目生成额外构建产物。
- PR/CI 状态按 `fix-ci-warning-cleanup` 分支查询；PR 检查前不预写全流水线通过。PR 的 API/浏览器 E2E 默认跳过，合并后 master 实际运行单独核对。
- 前端 lint/测试/构建通过不等于完整 Serverless 迁移或云端冷启动通过；本阶段不推进业务切流。

## 其他日志提示：尚未作为已解决项

进一步审阅日志还发现：Vite 大包、pnpm 未批准 esbuild 构建脚本；Python FastAPI lifespan/utcnow/TestClient 及 OSS SDK 字符串转义弃用；Android Compose 图标/menuAnchor；Maven 旧仓库/编译 add-opens、Mockito 动态 agent；部分 Action 内部 url.parse/punycode；第三方 FFmpeg 编译、证书重建及平台/测试 Mongo 环境提示。

这些不是刚才列出的 12 条 lint，也不能用忽略警告或扩大阈值宣称修复。后续按工具链/客户端/第三方依赖拆阶段，逐项验证；FFmpeg 升级需媒体与 GPU 兼容证据，不能为清日志直接改线上转码版本。GitHub 托管 runner 系统迁移和容量 notice 单独标记，不当成项目代码错误。

## 官方依据

- [upload-artifact v6](https://github.com/actions/upload-artifact/releases/tag/v6.0.0)
- [download-artifact v7 声明](https://github.com/actions/download-artifact/blob/v7/action.yml)
- [cache v5 声明](https://github.com/actions/cache/blob/v5/action.yml)

回退本阶段提交可恢复组件组织、lint 门禁和 Action 引用；本轮没有数据库结构或云资源修改。
