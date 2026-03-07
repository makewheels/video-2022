# 新功能设计文档

> 日期：2026-03-07  
> 状态：已确认  
> 工作方式：每个功能一个 PR，每次跑自动化测试，UI 模仿 YouTube

---

## 功能 1：视频删除

### 需求
用户可以删除自己上传的视频。

### 设计决策
| 问题 | 决定 |
|------|------|
| 删除方式 | **硬删除** — 直接删除 MongoDB 记录 |
| OSS 文件 | 检查 MD5 引用计数，仅当无其他视频引用同一 OSS 对象时才删除 |
| 播放列表 | 自动从所有包含该视频的播放列表中移除 PlayItem |
| UI 确认 | 需要确认弹窗 |

### 技术方案

**后端：**
- 新增 `DELETE /video/delete?videoId=xxx`（需认证，校验 uploaderId）
- 删除流程：
  1. 校验视频所有者
  2. 删除关联的 PlayItem 记录（通过 videoId 查找）
  3. 删除关联的 WatchLog 记录
  4. 删除 Transcode 记录
  5. 对每个关联 File：检查 MD5 是否有其他 File 引用（`hasLink` / `linkFileId`），无引用则删除 OSS 对象
  6. 删除 File 记录
  7. 删除 Video 记录

**前端：**
- 编辑页（edit.html）底部添加红色"删除视频"按钮
- 我的视频列表每个视频卡片增加删除按钮（垃圾桶图标）
- 确认弹窗："确定要删除这个视频吗？此操作不可恢复。"

### 数据变更
- `Video` 实体：无变更
- `File` 实体：删除时检查 `md5` 字段去重

---

## 功能 2：视频搜索

### 需求
在"我的视频"页面按标题和描述搜索视频。

### 设计决策
| 问题 | 决定 |
|------|------|
| 搜索范围 | 当前用户自己的视频 |
| 搜索字段 | 标题（主）+ 描述 |
| 搜索方式 | MongoDB `$regex` 模糊匹配 |
| UI 位置 | 我的视频页面顶部搜索框 |

### 技术方案

**后端：**
- 修改 `GET /video/getMyVideoList` 增加 `keyword` 参数
- 查询条件：`{ uploaderId: userId, $or: [{ title: { $regex: keyword, $options: 'i' } }, { description: { $regex: keyword, $options: 'i' } }] }`
- keyword 为空时返回全部（兼容现有逻辑）

**前端：**
- 我的视频页面顶部添加搜索输入框 + 搜索按钮
- 输入防抖 300ms，回车或点击触发搜索
- 搜索结果复用现有视频列表组件

### 数据变更
- 无新 collection
- Video 的 `title` 字段建议添加 MongoDB 文本索引（可选，regex 也能用）

---

## 功能 3：视频可见性

### 需求
视频有三种可见性级别，用户可在上传和编辑时设置。

### 设计决策
| 问题 | 决定 |
|------|------|
| 可见性级别 | `PUBLIC`（公开）、`UNLISTED`（仅链接可访问）、`PRIVATE`（私有） |
| 默认值 | `PUBLIC` |
| 私有视频访问 | 显示提示页面（"该视频为私有"） |
| 设置位置 | 上传时选择 + 编辑页可改 |

### 技术方案

**后端：**
- `Video` 实体新增 `visibility` 字段（String），默认 `"PUBLIC"`
- 创建时设置默认值
- `UpdateVideoInfoDTO` 新增 `visibility` 字段
- `WatchController.getWatchInfo` 和播放页：检查 visibility
  - `PUBLIC`：正常播放
  - `UNLISTED`：有 watchId 就能播放（不在公开列表中出现）
  - `PRIVATE`：仅 uploaderId 匹配才能播放，否则返回提示
- `getMyVideoList`：返回所有自己的视频（不受 visibility 影响）
- 未来公开搜索 API：只返回 `PUBLIC` 视频

**前端：**
- upload.html：添加可见性下拉框（公开/仅链接/私有），默认"公开"
- edit.html：添加可见性下拉框
- watch.html：如果私有且非所有者，显示"该视频为私有视频"提示页
- 我的视频列表：每个视频显示可见性图标（🌐公开 / 🔗仅链接 / 🔒私有）

### 数据变更
- `Video` 新增字段：`visibility` (String, indexed)
- 新增枚举/常量类：`VideoVisibility`

---

## 功能 4：转码进度展示

### 需求
上传和列表页展示视频处理状态。

### 设计决策
| 问题 | 决定 |
|------|------|
| 展示位置 | 上传页（上传完成后）+ 我的视频列表 |
| 更新方式 | 轮询 5 秒一次 |
| 超时 | 10 分钟后停止轮询 |
| 状态粒度 | 三态：上传中 → 转码中 → 就绪 |

### 技术方案

**后端：**
- 已有 `VideoStatus` 状态：CREATED → UPLOADING → TRANSCODING → READY
- 新增 API：`GET /video/getVideoStatus?videoId=xxx`（轻量级，只返回 status）
- 或复用 `getVideoDetail` 接口

**前端：**
- 上传页：文件上传完成后显示状态标签
  - "处理中…"（带旋转动画）
  - 轮询 `/video/getVideoStatus?videoId=xxx`
  - 变为 READY 后显示 ✅ "处理完成"，提供播放链接
  - 超过 10 分钟停止轮询，显示"处理超时，请稍后刷新"
- 我的视频列表：
  - READY 状态不显示标签
  - 非 READY 显示蓝色"处理中"标签
  - 自动轮询直到 READY

### 数据变更
- 无新字段，复用现有 `Video.status`

---

## 功能 5：评论系统

### 需求
两级评论系统（评论 + 回复），YouTube 风格。

### 设计决策
| 问题 | 决定 |
|------|------|
| 评论结构 | 两级：顶级评论 + 回复 |
| 评论点赞 | 支持 |
| 排序 | 按时间 / 按热度（点赞数） |
| 未登录 | 可查看，不可发表 |
| 审核 | 不审核，直接发布 |
| 删除权限 | 评论者删自己的 + 视频作者删任意 |

### 技术方案

**后端：**

新增 MongoDB collection `comment`：
```
{
  id: String,
  videoId: String (indexed),
  userId: String,
  userName: String,
  content: String,
  parentId: String (null=顶级评论, 非null=回复),
  replyToUserId: String,
  replyToUserName: String,
  likeCount: int (默认0),
  createTime: Date,
  updateTime: Date
}
```

新增 MongoDB collection `comment_like`：
```
{
  id: String,
  commentId: String (indexed),
  userId: String (indexed),
  createTime: Date
}
```
唯一索引：`{ commentId: 1, userId: 1 }`

API 端点：
- `POST /comment/add` — 发表评论（body: `{ videoId, content, parentId? }`）
- `GET /comment/getByVideoId?videoId=xxx&skip=0&limit=20&sort=time|hot` — 获取顶级评论
- `GET /comment/getReplies?parentId=xxx&skip=0&limit=20` — 获取某评论的回复
- `DELETE /comment/delete?commentId=xxx` — 删除评论
- `POST /comment/like` — 点赞评论（body: `{ commentId }`）
- `POST /comment/unlike` — 取消点赞
- `GET /comment/getCount?videoId=xxx` — 获取评论总数

**前端：**
- watch.html 播放器下方新增评论区域
- 评论输入框（需登录），YouTube 风格
- 评论列表：头像 + 用户名 + 时间 + 内容 + 点赞按钮 + 回复按钮
- 回复展开/收起
- 排序切换：最新 / 最热
- "加载更多"按钮分页

### 数据变更
- 新增 collection：`comment`
- 新增 collection：`comment_like`
- 新增 Java 类：`Comment`, `CommentLike`, `CommentVO`, `CommentController`, `CommentService`, `CommentRepository`

---

## 功能 6：视频点赞（Like/Dislike）

### 需求
YouTube 风格的 like/dislike 按钮。

### 设计决策
| 问题 | 决定 |
|------|------|
| 按钮 | Like + Dislike 两个按钮 |
| 显示 | 显示 like 数，不显示 dislike 数（YouTube 风格） |
| 位置 | 播放页视频标题下方 |

### 技术方案

**后端：**

新增 MongoDB collection `video_like`：
```
{
  id: String,
  videoId: String (indexed),
  userId: String (indexed),
  type: String ("LIKE" / "DISLIKE"),
  createTime: Date
}
```
唯一索引：`{ videoId: 1, userId: 1 }`

`Video` 实体新增字段：
- `likeCount` (int, 默认 0)
- `dislikeCount` (int, 默认 0)

API 端点：
- `POST /video/like?videoId=xxx` — 点赞（如果已 dislike 则切换）
- `POST /video/dislike?videoId=xxx` — 踩（如果已 like 则切换）
- `POST /video/cancelLike?videoId=xxx` — 取消点赞/踩
- `GET /video/getLikeStatus?videoId=xxx` — 获取当前用户的点赞状态

`getVideoDetail` 和 `getWatchInfo` 返回中增加 `likeCount` 字段。

**前端：**
- watch.html：视频标题行右侧添加 👍 / 👎 按钮
- 显示 like 数，dislike 不显示数字
- 当前用户已点赞/踩的按钮高亮
- 未登录点击提示"请先登录"

### 数据变更
- 新增 collection：`video_like`
- `Video` 新增字段：`likeCount`, `dislikeCount`

---

## 功能 7：分享按钮

### 需求
播放页提供复制链接功能。

### 技术方案

**前端：**
- watch.html：视频标题行添加"分享"按钮（📋 图标）
- 点击后复制当前视频的 watch URL 到剪贴板
- 显示 toast 提示"链接已复制"
- 使用 `navigator.clipboard.writeText()` API

**后端：**
- 无需改动

### 数据变更
- 无

---

## 实施顺序

每个功能一个 PR，按依赖关系排序：

| 顺序 | 功能 | 预计文件数 | 依赖 |
|------|------|-----------|------|
| PR 1 | 视频删除 | ~8 | 无 |
| PR 2 | 视频搜索 | ~4 | 无 |
| PR 3 | 视频可见性 | ~10 | 无 |
| PR 4 | 转码进度展示 | ~4 | 无 |
| PR 5 | 视频点赞 | ~10 | 无 |
| PR 6 | 评论系统 | ~15 | 无 |
| PR 7 | 分享按钮 | ~2 | 无 |

> 注：功能间无强依赖，但建议按上述顺序实施——先完善基础功能（删除、搜索、可见性），再添加社交功能（点赞、评论）。
