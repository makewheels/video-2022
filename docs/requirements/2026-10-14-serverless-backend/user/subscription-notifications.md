# M030–M032 订阅写入与通知HTTP

[固定任务台账](../overview/remaining-work.md) · [订阅读取基线](subscription-reads.md) · [验证记录](../verification.md)

归属S15，基线master `1b4eeb20`。本轮只交付订阅增删、通知列表/未读数、单条/全部已读的普通能力、现有Mongo适配及显式隔离HTTP；不切生产、不迁库、不创建云资源。旧Java源码和真实Spring MVC/Mongo生成的完整前后BSON是兼容依据，不按接口名称猜测鉴权或分页规则。

## 装配和依赖

`build_backend_application`新增`subscription_writes`、`notification_reads`、`notification_writes`三个开关，默认均为`False`。通知读写可分别启用，订阅写入不隐式打开订阅读取。与既有域共用同一个RequestCycle和同步请求/异常日志；默认工厂404且不访问数据库，原FC/ASGI生产入口不改。

源码核对纠正了旧清单的过宽依赖：subscribe/unsubscribe只同步写订阅和频道计数；四个通知HTTP只查询或窄写。它们不依赖S12可靠任务，也不新增后台线程。`NotificationService.sendNotification`目前没有生产调用者，本轮不凭方法声明新增“订阅后自动通知”等业务。

## 六个入口和旧身份策略

| 方法 | 路径 | 原身份要求 | 行为 |
|---|---|---|---|
| GET | `/subscription/subscribe` | 必须登录，缺失/坏token为HTTP401 | 必填channelUserId，先校验自己/频道/重复，再插入订阅并独立增数 |
| GET | `/subscription/unsubscribe` | 必须登录，缺失/坏token为HTTP401 | 必填channelUserId，先找记录，再删除并独立减数；不另查频道存在 |
| GET | `/notification/getMyNotifications` | **旧路由未安装CheckToken** | 可选token解析身份，page默认0、pageSize默认20且正数封顶50 |
| GET | `/notification/getUnreadCount` | **旧路由未安装CheckToken** | 只统计当前解析userId下BSON `read=false` |
| POST | `/notification/markAsRead` | **旧路由未安装CheckToken** | JSONObject读取notificationId，按`_id + toUserId`更新read |
| POST | `/notification/markAllAsRead` | **旧路由未安装CheckToken** | 按`toUserId + read=false`批量窄写，不解析请求正文 |

通知路由缺失/坏token沿用`userId=null`，查询包含原Mongo对null/缺失toUserId的匹配，不擅自改成401、伪造空结果或新增所有者规则。此处记录旧契约，不是新授权设计；未来若要收紧身份策略，应作为独立行为变更确认。

GET保留HEAD执行原业务（包括订阅增删）；OPTIONS沿旧路由身份策略返回Allow。错误方法在身份绑定前进入旧通用错误/异常日志。token仍可从旧请求头/查询参数解析，不新增凭据。

## 写入、类型和失败边界

- 订阅保留“不能订阅自己 / 频道不存在 / 已经订阅 / 未订阅”的直接错误和判断顺序。新记录由Mongo生成ObjectId；频道`subscriberCount`是Long，不能增加Java int范围校验。订阅引用精确匹配；实体`_id`沿Spring合法hex转ObjectId且不回退查同值String。
- 订阅插入/删除先于独立`$inc`，不是事务。显式null计数导致增数失败时，前序插入/删除保留；唯一索引冲突或计数失败均不隐式重试、不回滚为成功。只使用现有`uk_user_channel`语义，没有生产新增索引。
- 通知列表先find后count，不伪造原子快照；保留32位page乘积、非正pageSize不限条数、负skip按0、零pageSize的Java页数转换，不增加正数校验。
- 通知读取保留完整DTO字段、UTC毫秒日期与null。缺失read沿旧primitive默认false；显式null或不支持的BSON类型映射失败则记录异常并返回旧通用错误，不把它当false。未读计数/全部已读仍只匹配BSON false，不扩大到null/缺失。
- markAsRead沿旧泛型JSONObject绑定：缺字段/null、数字/布尔/容器转String、重复字段及错误正文由真实旧样本固定；合法hex仅匹配ObjectId而不回退同值String。缺匹配仍成功，包括传入null时对`_id=null`的旧查询，不能新增“必须非空”校验。
- 两种已读更新只写read，不整文档save、不改未知字段/日期；他人通知不因有相同ID而被修改，重复标记不新增副作用。markAllAsRead即使携带坏JSON也保持旧的不解析正文行为。

## 证据和回退

`InteractionHttpContractTest`真实运行旧Java MVC与独享回环Mongo，生成`serverless/contracts/interactions/`三份固定黄金样本；生成参数仅用于维护样本，正常测试再次从固定文件全文对照。Python在相同合成BSON上逐请求对照响应、业务前后状态和请求/异常日志，另验真实唯一索引竞争、计数失败不重放、默认关闭和完整读写回环。

实际命令、数量、CI和未验范围以[verification](../verification.md)、[progress](../progress.md)及本批PR当前head为准。真实FC/网络/冷热、Web/移动端实机及生产切流均不由本机或CI单测代替。M040另行验真实环境。

回退本批实现提交即可移除新隔离能力，已有赞踩/订阅读取基线不动。没有生产数据/配置变更，不恢复旧数据库备份覆盖业务数据。
