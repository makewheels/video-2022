# 业务、后台任务与定时任务的 FC 适配审计

状态：源码静态审计，未运行 Python 原型或云端迁移测试。基线及接口清单见 [README](README.md) / [routes](routes.md)。

## 1. 如何理解结论

- **可迁移**：代码路径未显示 FC 特有的业务阻碍，不代表已验证响应时间或跨语言兼容。
- **必须改造**：功能能实现，但当前进程内执行方式不能原样保留。
- **条件待验**：取决于网络、资源上限、HTTP 协议或实际数据量；通过前不能宣布完整可迁移。
- 搜索源码未发现现役 Controller 提供 WebSocket、SSE 或持续音视频转推；播放字节走 OSS。不存在因此必须长期维持连接的已发现业务要求。
- `new File()` 多处实际是业务实体 `file.bean.File`，不是本地文件依赖；下列磁盘风险以真正文件 I/O 为依据。

## 2. 接口与业务覆盖

源码路径以下均相对 `server/video/src/main/java/com/github/makewheels/video2022/`，YouTube 模块另行标明。

| 业务 / 源码入口 | 判定 | 迁移要求、不可变行为和验收重点 |
|---|---|---|
| `user/UserController`、`user/client`、`user/session` | 可迁移 | 用户/token/client/session 均有 Mongo 持久化；验证码在 Mongo，不依赖 Redis。沿用 TTL 之外的主动有效性校验，不新增登录规则；头像 STS/上传完成不遗漏 |
| `video/VideoController`、`video/service/VideoCreateService`、`RawFileService`、`LinkService` | 必须改造 | 创建后 YouTube 导入、封面和 rawFileUploadFinish 有后台线程；独立可靠任务执行，接口原有返回内容/状态不变。AUTO MD5 去重与 LOCAL 零云处理保持 |
| `video/VideoRepository`、`SearchController`、`CategoryController` | 可迁移 | Mongo regex、大小写、嵌套标签、visibility、sort/skip/limit 原样对应；避免迁移时换全文检索导致命中不同；查询索引和性能待测 |
| `video/service/VideoDeleteService` | 条件待验 | 逐项删除 Mongo 关联和 OSS 文件，读取大量 TS，耗时随历史规模增长；分批和恢复需保持原完成语义，不能擅自把已完成响应改为“已排队”。保留共享原片引用保护和删除前事件快照 |
| `file/FileController`、`FileService` | 必须改造 | STS 直传本身适配；`/file/access` 校验签名后 302 到 OSS，并 `CompletableFuture.runAsync` 写访问/费用；改为可靠入队或返回前完成，保持 302、原签名、费用事件一一对应 |
| `file/md5/Md5CfService`、`Md5CloudFunction` | 条件待验 | 主服务同步调用已有哈希 FC；耗时不能留在要求快速返回的接口中。`Md5CloudFunction` 有 OSS 挂载路径依赖，应核实实际部署/调用版本，不能只看 Java 类认定可在新运行时直接读取挂载文件 |
| `watch/play/WatchController`、`WatchService` | 条件待验 | 主/子 m3u8 是文本，不是普通 JSON；历史 480p、TS、fMP4 init/audio、CODECS/HDR、私密权限与授权上下文必须保留；每段 302 的冷启动是播放连续性风险，不能只测首页 |
| `watch/heartbeat`、`progress`、`watchhistory`、`playback` | 可迁移 | Mongo 持久化；保留两路 15 秒心跳、累计时长/暂停/退出和历史清理语义。`playback/exit` 接受 JSON 与 text/plain，须兼容 sendBeacon；页面还开着且暂停时也可能有请求，不等于闲置 |
| `playlist/PlaylistController` 及 item/list 服务 | 可迁移 | 新建、编辑、删除、恢复、排序、分页和关联列表全部保留；并发写避免旧快照覆盖。客户端 API v1 的 item/id 语义也保留 |
| `comment`、`video/like`、`subscription`、`notification` | 可迁移 | 计数增减、唯一复合索引、关联通知/回复、未读数需兼容；请求重试和跨实例下核对已有原子性，不增加重复副作用 |
| `share/ShareController` | 可迁移 | 保留代码实际的 `/share/s/{shortCode}` 跳转、点击统计及既有外部入口；不能误认成只有顶层 `/s/*`，需连同网关路由核对 |
| `developer/controller`、`developer/service` | 可迁移 | 应用 CRUD、密钥重建、应用 JWT、测试 Webhook 均覆盖；旧 Java JWT 密钥短字节补齐及序列化需跨语言测试，不把新库默认行为当兼容 |
| `openapi/oauth`、`openapi/v1` | 可迁移 | 开发者登录 JWT 与应用 JWT 是不同路径；BCrypt 旧 hash 验证、OAuth 持久化 token/refresh/revoke/scope、v1 HTTP 方法/错误码需逐项对照 |
| `openapi/ratelimit/RateLimitService` | 必须改造 | 当前 `ConcurrentHashMap` + 进程内 TokenBucket，Mongo 仅记录快照/配置，并非跨实例原子限流；换成 Mongo 原子共享限流或可证明等价的网关能力，保持阈值、补充规则、429 和剩余额度响应头 |
| `developer/service/DeveloperWebhookService`、`openapi/webhook/service/WebhookDispatchService` | 必须改造 | 两套路径分别使用 `@Async`、线程池和 sleep 退避；后者最多 5 次、2/4/8/16 秒退避。可靠任务代替进程内等待，保留事件、签名字节、顺序要求、次数、投递状态与失败可查 |
| `transcode/TranscodeController`、`task/*`、`TranscodeCallbackService` | 必须改造 | FC 媒体执行已存在；Python 迁移的是提交/回调/登记/恢复。保留 task/attempt 原子认领、manifest 目录/身份检查、租约、部分成功、重试和晚到回调规则；旧回调、LOCAL 登记也要承接 |
| `transcode/mps/MpsFallbackService`、`factory/AliyunMpsTranscodeImpl` | 必须改造 | 新链路有持久化轮询，legacy 另有后台 future 轮询；两条都不能遗漏。状态轮询变成任务触发且跨重启恢复，已提交 MPS job 不得重新付费提交 |
| `cover`、`cover/local` | 必须改造 | 自建 COVER、YouTube 封面搬运回调、LOCAL 凭证及完成登记都保留；不能移除原失败处理，不能用过时 video 快照覆盖 READY |
| `etc/statistics` | 条件待验 | Mongo 聚合保留时间分组、时区、求和与字段；按实际历史规模测聚合耗时、响应大小。不能擅自改成异步报表或改变分页 |
| `etc/app/AppController`、`AppService` | 可迁移 | 检查版本/管理员发布版本是 Mongo 元数据和下载 URL，无本机安装包传输需求；保持强制更新规则与管理权限 |
| `oss/osslog/OssLogController` | 条件待验 | 日期范围补采在一个 HTTP 请求中逐日执行；大范围可能超时。应在保持既有契约前提下分批处理；若必须变成“提交任务”接口，列为阻塞决策，不静默变更 |
| `springboot/exception`、`health`、`startup`、请求日志拦截器 | 必须改造 | 健康和异常查询可迁移；请求日志保持必要归属与查询能力并脱敏。冷启动每次写启动日志/机器信息不是用户业务，避免无意义同步 Mongo 操作，但日志字段消费者先核对 |
| `springboot/SpaController`、网关 `/w`、`console/` | 托管迁移 | 静态站路由回退到 index.html；`/w?v=`、开发者门户及 API 路径不能误落到 SPA。静态页面与业务 API 分开缓存，不能缓存私密 m3u8/鉴权结果为公共内容 |
| `utils/IpService`、`IdService`、`system/context/*` | 可迁移但需适配 | IP 缓存在 Mongo；ID 自增已用 Mongo findAndModify，不能因 synchronized 就判为只能单机。保留生成格式/时区；ThreadLocal 改请求隔离上下文，真实 IP/headers 来自可信 FC/网关来源 |
| `server/youtube/.../YoutubeController`、`YoutubeService` | 条件待验 | 4 个下载/信息接口及 2 个健康入口独立核对；主业务代码已经指向香港 FC 异步调用，实际线上配置未回读。yt-dlp/外部 API/临时文件/长下载需香港区验证，不能声称北京普通 API 函数可直接取代 |

## 3. 全部定时入口

确认 `VideoApplication` 启用 scheduling；扫描发现 6 处有效 `@Scheduled`，另有 1 处注释占位。它们都能由 FC 任务承载，但触发、重试和幂等需要重新实现。

| 当前入口 | 当前调度 / 行为 | FC 迁移要求 |
|---|---|---|
| `finance/bill/CreateBillTask.createAccessFeeBill` | 每日 00:00，生成访问账单并扣钱包 | 独立定时任务、分页按用户/账期处理；当前 dev 分支取当天到明天，其他分支取昨天到今天。生产历史运行 dev profile，不能迁移时顺手改账期 |
| `oss/osslog/GenerateOssAccessLogTask.generateOssAccessLog` | 每日 00:00，采集两天前日志 | 时间触发分发对象任务，按文件/批次持久化进度。保留两天滞后和补采能力；不能只收到一个 OSS 事件就认为完整一天 |
| `oss/inventory/GenerateInventoryTask.generateInventory` | 每日 02:00，处理当天清单 | manifest 与 CSV/gz 分批、流式解压/读取和 Mongo 批量写入；保留时区及 UTC 前缀转换；处理中断不能因父记录已存在而永久跳过 |
| `transcode/task/FcTimeoutRecoveryService.recover` | fixedDelay 60 秒 | 保留 CREATED/SUBMITTED/RETRY_WAIT/PROCESSING 恢复；可靠 deadline 触发代替永久空扫，必须证明“写 Mongo 成功、发消息前崩溃”也不会丢任务 |
| `transcode/mps/MpsFallbackService` | fixedDelay 30 秒 | 仅为活跃 MPS job 安排下一次检查；保持恢复时效。不能未经确认直接换成 1 分钟轮询；当前定时触发器间隔能力及延迟消息粒度须原型确认 |
| `openapi/ratelimit/RateLimitService.cleanupIdleBuckets` | fixedRate 300 秒，清理 JVM map | 共享限流实现后不再照搬本地 map 清理。TTL 只能清旧数据，不能代替业务窗口/补充算法判定 |
| `video/ChangeStorageClassTask.run` | `@Scheduled` 被注释，方法内是未完成占位 | 不属于正在运行的任务；迁移不得擅自启用删除/降级存储功能 |

每日任务本身是业务执行，保留后即使用户当天未访问仍会有少量调用。需要用户无访问时完全零定时调用，与保持现有定时业务完全一致并不等价；本提案优先保持业务，不删除这些任务。

### 财务是上线阻塞项，不是简单换调度

`finance/transaction/TransactionService.createTransaction` 逐笔扣余额、写交易、更新账单；`WalletService.changeBalance` 读余额后整对象保存。源码没有展示跨这些写入的事务；且循环内使用整个 billIds 更新账单。自动重试/并发调度会放大重复扣款、丢失更新和关联错误风险。

迁移前先用固定数据证明现有计算/关联结果，再针对投递重试实现可恢复的单次扣款。需要核实 Mongo 是 standalone 还是 replica set、是否支持事务；不能假定有事务。若要增加幂等记录/唯一索引/技术状态，提交最小变更设计确认；不改变金额、舍入、账期和用户可见规则，也不把现有问题作为重写需求擅自扩大。

## 4. 不能按原样迁移的执行方式

1. **请求返回后依靠本地线程继续工作**：`VideoService`、`VideoCreateService`、`FileService` 和两套 Webhook；函数冻结/销毁会中断，必须用可靠任务或返回前完成。
2. **内存是唯一状态来源**：限流 TokenBucket，不能在新实例重置配额；普通无状态 provider 工厂 map 不需要持久化。
3. **请求内长期 sleep / 状态轮询**：legacy MPS 与 Webhook；等待应交给平台调度，不占着函数等。
4. **全量文件/数据驻内存**：`OssLogService` 整文件字符串、`CompressUtil` 全量解压、`OssInventoryService` 全量 CSV/列表、账单全量查询；要流式/分页，测实际最大历史批次。
5. **本地持久目录和外部命令**：YouTube 临时文件可放 FC 临时目录，处理成功/失败清理，重试从 OSS/任务恢复；ffmpeg/yt-dlp 不进入普通 API 包。临时盘不能当跨调用持久卷。
6. **依赖原服务器的静态路由和配置读取**：SPA、console、短链和内部 URL/回调必须由新入口承接；取密在部署时注入或经受控云能力获取，不每请求访问旧 Infisical 服务。

## 5. FC 的真实边界与未确认项

以下为 2026-10-14 官方资料的能力边界，不是本账户已验证配额：

- [资源限制](https://help.aliyun.com/zh/functioncompute/limits-of-usage)：当前文档列 CPU 临时盘 10GB、GPU 60GB、最长函数执行 86400 秒；同步请求 32MB，异步 payload 默认 128KB。实际选择的函数配置可更小；现有自建转码部署 timeout 为 1800 秒，不能把平台上限当当前任务超时。
- YouTube 原片、下载/合并中间文件若超过可用临时盘，或任务超过允许执行时间，原单任务实现无法承载；需验证流式传输/可恢复分段在不改变文件及业务行为的前提下成立，否则对应任务是未解决阻塞，不能直接拒绝原可用输入来宣布迁移完成。
- [HTTP 内置运行时](https://help.aliyun.com/zh/functioncompute/http-trigger-invoking-function) 使用 event/response 映射，必须处理 base64 body、header 大小写、方法及查询参数；[HTTP 触发器](https://help.aliyun.com/zh/functioncompute/http-triggers-overview) 默认域名的 attachment 行为不能作为正式站点入口。用正式自定义域名验 m3u8、302、CORS、sendBeacon。
- `getOssObjectMetadataByKey` 返回 `OSSObject` 包装，需录制实际 Java 响应；`exceptionLog/getById` 和 `heartbeat/add` 使用 RequestMapping，不能擅自只接受一个方法。框架默认序列化/路由文档不是现有客户端契约。
- `RequestUtil.getSession/getSessionId` 搜索未发现业务调用者；当前不能仅因有 Servlet HttpSession helper 就判依赖粘性会话。用户/设备 session 是独立业务实体，必须保留。
- JWT、HMAC、BCrypt、BigDecimal、BSON 类型和时区需跨语言黄金样本；Python 字符串/JSON 默认形式可能导致签名或响应不同。
- 数据库在腾讯云私网时 FC 不可直接连接，公网/互联/迁阿里云是网络决策。固定公网出口常涉及 NAT/EIP，可能闲置也计费，不能纳入“无请求计算费归零”而不单独报价。
- 队列/异步执行是至少一次语义，不是业务恰好一次；参考 [MNS 队列](https://help.aliyun.com/zh/mns/developer-reference/queue-queue)、[FC 集成](https://help.aliyun.com/zh/mns/user-guide/function-compute-fc)。延迟投递、可见性/租约、死信和数据库写入至投递间隙都要故障验证。
- 源码与文档不能证明当前云资源、Mongo 拓扑、实际最大输入、账户配额、默认头部和冷启动延迟。本次没有登录线上检查或部署收费原型。

## 6. 最终判断

普通查询/写入 API、STS、现有 OSS 播放、媒体回调都没有已发现的不可迁移业务。必须优先验证的六项是：**真实冷启动与 Mongo 网络、HLS HTTP 兼容、财务幂等、无空轮询的可靠恢复、全量批处理资源边界、香港 YouTube 大文件/长任务**。

只有逐接口契约、跨端原有流程、历史媒体和故障恢复都通过后，才能给出“业务完全一致并可替代旧服务”的上线结论。任何一项只能通过改变原有业务才能完成，都回到未决阻塞处理。
