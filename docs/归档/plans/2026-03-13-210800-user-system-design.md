# 用户体系完善设计文档

## 概述

为视频平台增加完整的用户体系，包括：用户资料（昵称、头像、简介）、频道系统（频道页、Banner）、订阅/关注功能。覆盖后端API + Web前端 + Android + iOS 全平台。

## 数据模型

### User 实体扩展

在现有 `User` 文档（MongoDB）中新增字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `nickname` | String | 频道名称，默认脱敏手机号（138****0000） |
| `avatarUrl` | String | 头像URL（OSS地址），可为空 |
| `bannerUrl` | String | 频道Banner图URL，可为空 |
| `bio` | String | 个人简介，限200字 |
| `subscriberCount` | Long | 订阅者数量（冗余字段，避免频繁count） |
| `videoCount` | Long | 视频数量（冗余字段） |

### 新增 Subscription 实体

```
Subscription {
    id: String (MongoDB ObjectId)
    userId: String (订阅者ID)
    channelUserId: String (被订阅的频道用户ID)
    createTime: Date
}
```

索引：
- `(userId, channelUserId)` 联合唯一索引
- `channelUserId` 单独索引（查询某频道的订阅者）
- `userId` 单独索引（查询某用户的订阅列表）

## API 设计

### 用户资料 API

| 端点 | 方法 | 认证 | 说明 |
|------|------|------|------|
| `POST /user/updateProfile` | POST | 需要 | 更新昵称、简介 |
| `POST /user/uploadAvatar` | POST | 需要 | 获取头像上传OSS凭证，上传完成后更新User.avatarUrl |
| `POST /user/uploadBanner` | POST | 需要 | 获取Banner上传OSS凭证，上传完成后更新User.bannerUrl |
| `GET /user/getChannel` | GET | 不需要 | 获取频道页信息（userId → 用户资料+视频数+订阅数） |

`updateProfile` 请求体：
```json
{
  "nickname": "新昵称",
  "bio": "个人简介内容"
}
```

`getChannel` 响应：
```json
{
  "userId": "xxx",
  "nickname": "频道名",
  "avatarUrl": "https://...",
  "bannerUrl": "https://...",
  "bio": "简介",
  "subscriberCount": 1234,
  "videoCount": 56,
  "isSubscribed": false
}
```

### 订阅 API

| 端点 | 方法 | 认证 | 说明 |
|------|------|------|------|
| `GET /subscription/subscribe` | GET | 需要 | 订阅频道（channelUserId） |
| `GET /subscription/unsubscribe` | GET | 需要 | 取消订阅 |
| `GET /subscription/getStatus` | GET | 需要 | 查询是否已订阅某频道 |
| `GET /subscription/getMySubscriptions` | GET | 需要 | 获取我的订阅列表（分页） |
| `GET /subscription/getSubscribers` | GET | 不需要 | 获取频道订阅者列表（分页） |

### VideoVO 增强

- `uploaderName`：优先取 User.nickname，fallback 到脱敏手机号
- 新增 `uploaderAvatarUrl`：取 User.avatarUrl

### Comment 显示增强

- 评论返回 `userNickname` 和 `userAvatarUrl`（替代当前的 `userPhone`）

## 前端设计

### Web 前端

1. **频道页** `/channel/:userId`
   - 顶部Banner图（全宽）
   - 头像 + 频道名 + 简介 + 订阅按钮（显示订阅数）
   - Tab切换：视频列表 | 播放列表
   - 视频列表复用 PublicVideoCard 组件

2. **个人设置页** `/settings`（需登录）
   - 编辑昵称、简介
   - 上传头像（裁剪为圆形预览）
   - 上传Banner
   - 保存按钮

3. **NavBar 改进**
   - 登录后：👤图标改为用户真实头像
   - 点击头像显示下拉菜单：我的频道、设置、退出

4. **视频卡片**
   - PublicVideoCard 的头像从首字母圆形改为真实 `uploaderAvatarUrl`
   - 点击头像/用户名跳转到 `/channel/:userId`

5. **评论区**
   - 显示用户昵称和头像
   - 点击头像跳转频道页

### Android

1. **频道页 Screen** — 从视频卡片点击头像进入
2. **设置页** — 增加"编辑资料"入口
3. **VideoCard** — 显示上传者头像，点击进入频道
4. **数据层** — VideoItem 增加 uploaderAvatarUrl，新增 Subscription API

### iOS

1. **频道页 View** — 同Android
2. **设置页** — 增加编辑资料
3. **VideoCard** — 显示头像
4. **模型层** — VideoItem 增加字段，新增订阅API

## SPA 路由

SpaController 新增：`/channel/**`, `/settings`

## 图片上传流程

复用现有 OSS 直传流程：
1. 前端调用 `/user/uploadAvatar` 获取 STS 临时凭证
2. 前端直传图片到 OSS
3. 前端调用回调接口确认上传完成
4. 后端更新 User.avatarUrl

Banner 同理。

## 认证拦截器更新

CheckTokenInterceptor 新增需要登录的路径：
- `/user/updateProfile`
- `/user/uploadAvatar`
- `/user/uploadBanner`
- `/subscription/subscribe`
- `/subscription/unsubscribe`
- `/subscription/getStatus`
- `/subscription/getMySubscriptions`
