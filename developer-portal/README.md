# Video Platform 开发者门户

开发者门户 —— 用于管理 OAuth 应用、查看 API 文档和用量统计。

## 技术栈

- React 19 + TypeScript
- Vite
- React Router
- TanStack React Query

## 开发

```bash
# 安装依赖
npm install

# 启动开发服务器 (默认端口 5173)
npm run dev

# 构建生产版本
npm run build

# 预览生产构建
npm run preview
```

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `VITE_API_BASE_URL` | 后端 API 地址 | `''` (通过 Vite proxy 转发) |

## 后端 API

开发模式下，Vite 会将以下路径代理到 `http://localhost:5022`：

- `/developer/*` — 开发者认证和应用管理
- `/oauth/*` — OAuth 流程
- `/api/*` — Swagger 文档

请确保后端服务已启动在 `http://localhost:5022`。
