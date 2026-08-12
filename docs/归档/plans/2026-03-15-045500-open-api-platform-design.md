# 开放 API 平台 设计文档

## 概述

将 video-2022 打造为对外开放接口的平台，类似 YouTube API，第三方应用可通过 HTTP 接口接入视频上传、播放、管理等全部功能。

## 决策记录

| 决策项 | 选择 |
|--------|------|
| 平台定位 | 开放平台（类似 YouTube API，第三方应用接入） |
| 开放范围 | 全部接口（视频、评论、播放列表、用户、统计等） |
| 认证方式 | OAuth 2.0 |
| 频率限制 | 需要 |
| Webhook | 需要（转码完成等事件通知） |
| API 路径 | `/api/v1/` 独立路径，不影响现有接口 |
| API 文档 | Swagger/OpenAPI 自动生成 |
| 计费 | 复用现有计费系统（按量计费） |
| 实施节奏 | 一步到位，全部一起做 |
| 开发者门户 | 新建 `developer-portal/` 目录，独立于现有 `frontend/` |

---

## 1. 整体架构

```
┌──────────────────────────────────────────────────────────────┐
│                    开发者门户 (developer-portal/)             │
│                                                              │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │
│  │ 注册登录 │ │ 应用管理 │ │ API 文档 │ │ 用量统计 │       │
│  │          │ │ API Key  │ │ Swagger  │ │          │       │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘       │
└──────────────────────────┬───────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                    后端 API 网关层                            │
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │ OAuth 2.0    │  │ Rate Limiter │  │ API Version  │       │
│  │ 认证/授权    │  │ 频率限制     │  │ /api/v1/     │       │
│  └──────────────┘  └──────────────┘  └──────────────┘       │
│                                                              │
│  ┌──────────────────────────────────────────────────┐       │
│  │              /api/v1/ RESTful 接口                │       │
│  │                                                    │       │
│  │  /api/v1/videos          视频管理                  │       │
│  │  /api/v1/videos/{id}/transcode  转码状态          │       │
│  │  /api/v1/videos/{id}/play       播放地址          │       │
│  │  /api/v1/comments        评论管理                  │       │
│  │  /api/v1/playlists       播放列表                  │       │
│  │  /api/v1/users           用户信息                  │       │
│  │  /api/v1/statistics      统计数据                  │       │
│  │  /api/v1/webhooks        Webhook 配置             │       │
│  └──────────────────────────────────────────────────┘       │
│                                                              │
│  ┌──────────────────────────────────────────────────┐       │
│  │              Webhook 事件推送                     │       │
│  │  video.transcode.completed                       │       │
│  │  video.upload.completed                          │       │
│  │  video.deleted                                   │       │
│  └──────────────────────────────────────────────────┘       │
└──────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────┐
│              现有业务层（不改动）                              │
│  VideoService, FileService, TranscodeService, etc.          │
│  MongoDB, Aliyun OSS, 计费系统                               │
└──────────────────────────────────────────────────────────────┘
```

---

## 2. OAuth 2.0 认证体系

### 数据模型

#### Developer（开发者账户）
```json
{
  "_id": "ObjectId",
  "email": "dev@example.com",
  "passwordHash": "bcrypt...",
  "name": "开发者名称",
  "company": "公司名（可选）",
  "status": "active",
  "createTime": "ISODate",
  "updateTime": "ISODate"
}
```

#### OAuthApp（开发者创建的应用）
```json
{
  "_id": "ObjectId",
  "developerId": "开发者ID",
  "name": "我的应用",
  "description": "应用描述",
  "clientId": "随机生成",
  "clientSecret": "随机生成（加密存储）",
  "redirectUris": ["https://myapp.com/callback"],
  "scopes": ["video:read", "video:write", "comment:read"],
  "rateLimitTier": "standard",
  "status": "active",
  "createTime": "ISODate"
}
```

#### OAuthToken（访问令牌）
```json
{
  "_id": "ObjectId",
  "appId": "应用ID",
  "userId": "授权用户ID（可选，client_credentials 模式无此字段）",
  "accessToken": "JWT or 随机token",
  "refreshToken": "刷新token",
  "scopes": ["video:read", "video:write"],
  "expiresAt": "ISODate",
  "createTime": "ISODate"
}
```

### OAuth 2.0 授权流程

支持两种模式：

1. **Client Credentials** — 应用自己的操作（上传视频到应用自己的空间）
2. **Authorization Code** — 代表用户操作（读取用户的视频列表）

### 权限范围 (Scopes)

| Scope | 描述 |
|-------|------|
| `video:read` | 查看视频信息、列表、播放地址 |
| `video:write` | 上传、更新、删除视频 |
| `video:transcode` | 查看转码状态 |
| `comment:read` | 查看评论 |
| `comment:write` | 发表、删除评论 |
| `playlist:read` | 查看播放列表 |
| `playlist:write` | 管理播放列表 |
| `user:read` | 查看用户信息 |
| `statistics:read` | 查看统计数据 |
| `webhook:manage` | 管理 Webhook 配置 |

---

## 3. /api/v1/ RESTful 接口设计

### 视频接口

| Method | Path | Scope | 描述 |
|--------|------|-------|------|
| POST | /api/v1/videos | video:write | 创建视频（获取上传凭证） |
| GET | /api/v1/videos | video:read | 获取视频列表 |
| GET | /api/v1/videos/{id} | video:read | 获取视频详情 |
| PATCH | /api/v1/videos/{id} | video:write | 更新视频信息 |
| DELETE | /api/v1/videos/{id} | video:write | 删除视频 |
| POST | /api/v1/videos/{id}/upload-credentials | video:write | 获取上传凭证 |
| POST | /api/v1/videos/{id}/upload-complete | video:write | 通知上传完成 |
| GET | /api/v1/videos/{id}/transcode | video:transcode | 获取转码状态 |
| GET | /api/v1/videos/{id}/play | video:read | 获取播放地址 |
| GET | /api/v1/videos/{id}/statistics | statistics:read | 获取视频统计 |

### 评论接口

| Method | Path | Scope | 描述 |
|--------|------|-------|------|
| GET | /api/v1/videos/{id}/comments | comment:read | 获取视频评论 |
| POST | /api/v1/videos/{id}/comments | comment:write | 发表评论 |
| DELETE | /api/v1/comments/{id} | comment:write | 删除评论 |

### 播放列表接口

| Method | Path | Scope | 描述 |
|--------|------|-------|------|
| POST | /api/v1/playlists | playlist:write | 创建播放列表 |
| GET | /api/v1/playlists | playlist:read | 获取播放列表 |
| GET | /api/v1/playlists/{id} | playlist:read | 获取播放列表详情 |
| PATCH | /api/v1/playlists/{id} | playlist:write | 更新播放列表 |
| DELETE | /api/v1/playlists/{id} | playlist:write | 删除播放列表 |
| POST | /api/v1/playlists/{id}/items | playlist:write | 添加视频到列表 |
| DELETE | /api/v1/playlists/{id}/items/{itemId} | playlist:write | 从列表移除视频 |

### 用户接口

| Method | Path | Scope | 描述 |
|--------|------|-------|------|
| GET | /api/v1/users/me | user:read | 获取当前用户信息 |
| GET | /api/v1/users/{id} | user:read | 获取用户公开信息 |

### Webhook 接口

| Method | Path | Scope | 描述 |
|--------|------|-------|------|
| POST | /api/v1/webhooks | webhook:manage | 注册 Webhook |
| GET | /api/v1/webhooks | webhook:manage | 获取 Webhook 列表 |
| DELETE | /api/v1/webhooks/{id} | webhook:manage | 删除 Webhook |

---

## 4. Rate Limiting 频率限制

### 限流策略

使用 Redis 实现滑动窗口限流：

| 级别 | 请求限制 | 适用 |
|------|---------|------|
| standard | 100 次/分钟, 1000 次/小时 | 默认 |
| premium | 500 次/分钟, 5000 次/小时 | 付费开发者 |
| unlimited | 无限制 | 管理员/内部 |

### 响应头

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1616112000
```

超限时返回 `429 Too Many Requests`。

---

## 5. Webhook 事件推送

### 事件类型

| 事件 | 触发条件 |
|------|---------|
| `video.upload.completed` | 视频上传完成 |
| `video.transcode.completed` | 视频转码完成 |
| `video.transcode.failed` | 视频转码失败 |
| `video.deleted` | 视频被删除 |

### Webhook 推送格式

```json
{
  "event": "video.transcode.completed",
  "timestamp": "2026-03-15T12:00:00Z",
  "data": {
    "videoId": "v_123",
    "transcodeId": "t_456",
    "resolution": "1080p",
    "status": "READY"
  },
  "signature": "HMAC-SHA256签名"
}
```

- 使用 HMAC-SHA256 签名验证
- 失败自动重试（指数退避，最多 5 次）

---

## 6. Swagger/OpenAPI 文档

- 使用 springdoc-openapi 自动生成
- 访问地址: `/api/docs` (Swagger UI), `/api/v1/openapi.json` (OpenAPI spec)
- 在开发者门户中嵌入

---

## 7. 开发者门户网站 (developer-portal/)

### 技术栈

- React + Vite + TypeScript（与现有 frontend/ 保持一致）
- 独立部署

### 功能页面

1. **首页** — 平台介绍、快速开始
2. **注册/登录** — 开发者账户管理
3. **应用管理** — 创建应用、获取 clientId/clientSecret
4. **API 文档** — 嵌入 Swagger UI
5. **用量统计** — API 调用次数、计费明细
6. **Webhook 配置** — 管理回调地址

---

## 8. 计费集成

复用现有计费系统：
- 第三方 API 调用产生的费用归属到对应开发者的钱包
- 视频存储、转码、下载均按现有费率计费
- 开发者门户显示用量和账单
