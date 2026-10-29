# S15.a 视频点赞与点踩

状态：隔离实现、本机验收及实现提交必需 CI 完成（2026-10-29），[PR #229](https://github.com/makewheels/video-2022/pull/229) 待合并，未切生产。属于既有 Java → Python/FC 迁移，不新增产品行为；基线为 `cbc02da7`。

## 目标与边界

迁移 `GET /videoLike/like`、`GET /videoLike/dislike`、`GET /videoLike/getStatus`，交付普通 Python 能力、现有 Mongo 适配和显式隔离 HTTP 装配。复用同一请求身份、异常记录与同步请求日志。默认入口和生产路由不变，不创建云资源、迁库或改客户端用法。

旧实现证据为 `VideoLikeController`、`VideoLikeService`、`VideoLike`、`InterceptorConfiguration`。先增加真实 Spring HTTP/Mongo 黄金样本，再实现 Python 对照；不能把 API 文档中不准确的描述当作新规则。

## 必须保留的旧行为

- 只有 like/dislike 要求登录；getStatus 公开，登录时附带当前用户状态。登录检查先于参数绑定和视频存在检查，三个入口都只检查视频存在，不增加可见性/所有者校验。
- 同一操作删除记录后递减计数；相反操作先整文档保存新 type/updateTime，再分别递减旧计数、递增新计数；新操作先保存记录再递增。两次计数不是一次原子更新。
- 沿用 `video_like`、`videoId + userId` 唯一键和 BSON ObjectId ID；引用字段仍按原字符串匹配。新建分别取创建/更新时间，切换保留创建时间，保存省略 null 并移除实体外字段。
- 状态读取将缺失/null 计数显示为零，写入仍遵从 Mongo `$inc` 的真实行为。null type、非法计数、重复键及部分写入不得改成成功、事务回滚或业务重试。
- 固定完整 HTTP 正文、状态、Content-Type、请求/异常日志、参数/身份优先级及 HEAD/OPTIONS/不支持方法；不照搬框架默认 405/422。

## 验收与回退

使用系统临时目录内独立 loopback Mongo，真实旧 Java 导出合成样本，Python 逐项比较响应及操作前后完整 BSON。覆盖六种 toggle、独立用户、缺失/空参数、无效身份、旧 ID 转换、缺失/null/数字计数、唯一键竞争和失败后的部分写入。运行相关路由回归、架构边界、Ruff、Java Checkstyle 和文件长度检查。

### 本次实际结果

- Windows CPython 3.12.12、JDK 21.0.10、独立 MongoDB 8.3.7（仅 loopback 临时端口）：旧 Java 赞踩服务/HTTP 12 项通过，0 skipped，包含 56 组 HTTP/完整 BSON 黄金样本；生成后再次不带导出参数重放，固定文件对照通过。
- Python 本阶段新增 62 项，连同评论、统一播放/观看装配和架构边界共 **180 passed / 0 skipped**；已有 Starlette/httpx 弃用提醒 1 条。Ruff、Java Checkstyle、500 行门禁和 `git diff --check` 通过。
- 真正的默认工厂未启用新开关时三个路由及 OPTIONS 均为 404，且不访问数据库；显式 `video_reactions=True` 才装配，共享用户 RequestCycle。
- 真实 Mongo 重复键竞争、增数故障、先写后失败和独立用户切换均实际执行。未连接生产 Mongo、调用付费服务或改生产配置。

实现提交 `d2aea26b` 的 [CI 40841352861](https://github.com/makewheels/video-2022/actions/runs/40841352861) 实际 success：Linux 完整 Python **1400 passed / 0 skipped / 1 warning**，Linux 来源/可重现包与其他必需检查通过；API/浏览器 E2E 按既有条件 skipped。收尾文档提交的当前 head CI 继续以 PR 为准。真实 FC/Mongo 网络、云端冷热性能和 Web/移动端实机仍未跑；上述通过不表示 S15 整体完成或可退役 Java。回退本阶段 PR 提交即可，禁止恢复旧数据库备份覆盖新业务数据。
