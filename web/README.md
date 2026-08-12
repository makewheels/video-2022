# video-2022 Web 前台

视频分享平台的用户前台 SPA：上传、播放、搜索、评论、播放列表、个人中心，以及 AI 助手聊天页。

## 技术栈

- React 19 + TypeScript + Vite 7
- Vitest + Testing Library（单元测试，`tests/`）
- Playwright（浏览器 E2E，`e2e/`）

## 常用命令

```bash
pnpm install          # 安装依赖
pnpm dev              # 开发服务器（端口 5173，API 代理到 localhost:5022）
pnpm build            # 生产构建（tsc -b && vite build）
pnpm lint             # ESLint
pnpm exec vitest run  # 单元测试（CI 使用同一命令）
```

## 浏览器 E2E（本地手动，不在 CI）

`e2e/` 目前只覆盖 AI 聊天页（`chat.spec.ts`），不进 CI、无 npm script。运行前提：

1. `video_agent` Python 包可被当前解释器导入（Playwright 配置会执行 `python3 -m video_agent serve`）：

   ```bash
   cd ../ai-agent && uv sync && source .venv/bin/activate && cd ../web
   ```

2. 在 `web/e2e/` 下运行（`playwright.config.ts` 会自动拉起 agent 服务 8765 和 `pnpm dev` 5173）：

   ```bash
   cd e2e && pnpm exec playwright test
   ```

## 目录

```text
web/
├── src/        # 页面、组件、API 调用
├── tests/      # Vitest 单元测试
├── e2e/        # Playwright 用例（本地手动）
└── public/     # 静态资源
```
