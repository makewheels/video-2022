# S09.g 观看日志创建与计数HTTP基线

状态：实施中。固定旧`GET /watchController/addWatchLog`真实MVC/隔离Mongo行为，不修改运行代码、不连接生产。

重点：Controller先检查视频存在；服务按video/session/请求videoStatus去重，READY请求先自增计数；保存时记录视频实际status，先查IP再写watchLog，之后又查IP用于日志输出。需实测状态不一致导致重复、不同身份共享去重、匿名私密视频、第一/二次IP失败后部分写入及再次请求行为。

外部IpService使用固定合成返回/指定调用异常；这属于受控故障，不是真实IP供应商网络验收。不能把普通未知错误解释成事务回滚，迁移不得自动重放业务写。Python、具体IP服务/缓存、完整HTTP和真实FC仍另验。

## 2026-10-16实测

`WatchLogHttpContractTest`真实MVC/隔离Mongo6passed/0skipped、Checkstyle成功，500行/空白通过。没有改Python，所以未重复其最近552项全量。

- 匿名可给私密视频记日志/增数；不同viewer/client按同video/session/status共用去重，null session也如此；XFF不替代remoteAddr，IP详情额外字段保存。
- 请求READY控制增数，日志保存视频实际status；不一致会连续重复日志/计数。增数后实际BSON为Long，首轮Integer读取假设报错，已纠正为显式Long断言再全量复验。
- 第一IP调用合成失败：HTTP200/code1、计数保留且无日志；另一次用户请求可再增数。第二调用合成失败：计数/日志均保留，后续相同请求命中去重并成功，不再查IP。没有在业务内部自动重试。
- Controller视频存在检查先于去重；缺videoStatus在绑定期拒绝，无计数/日志写入。

这些是旧运行行为与合成IP故障基线，不是真实外部网络故障，不在生产重放；具体IP适配/缓存和Python能力尚未实现。

## S09.h 普通用例与Mongo写入

实施中：普通WatchVisit/WatchEvent/WatchVideo值，视频存在/去重/读取/增数/保存端口，IP查询独立端口；组合保留两次IP和先计数后写入、错误不重试。Mongo增数显式Int64(1)，旧ObjectId与String不回退；事件保存使用实际视频ID/status，null省略，IP完整JSON递归保留。补旧HTTP IP映射/ID边界，之后实际隔离Mongo对照；具体外部IP与缓存、HTTP/生产入口仍不装配。

### S09.h本地验收

旧Java真实HTTP/Mongo9passed/0skipped、Checkstyle通过，新增IP JSON递归null/Decimal128/Long/list、含点键保存失败顺序、大写ObjectId请求保存小写ID而不去重。

完整Python564passed/0skipped/1既有warning，新增12项真实Mongo与合成IP故障对照，Ruff/500行/空白通过。首次架构门禁拒绝copy依赖（其余563项通过），移除不必要copy导入后按原门禁全量复验；没有放宽架构规则。

验证先存在检查/去重、BSON Int64增数、实际视频ID/status、递归IP转换、第一次/第二次IP故障与事件保存后合成确认异常不重放；null watch子文档真实Mongo失败在IP前发生。后两类及IP null组合是源码/新实现验证，不宣称旧HTTP逐项覆盖。具体IP云服务/缓存、观看日志HTTP和真实网络/FC未实现或未验，不安装生产入口。

## S09.i（09/14）IP查询与缓存

实施中：复用旧ip_cache集合、ip唯一键和createdAt六小时TTL定义；运行请求不新建索引、不按时间主动判过期，缓存记录仍存在即复用。IPv6冒号替换为下划线。缓存端口/普通record、Mongo适配、外部API适配与组合分开，业务仍只依赖IpLocations。

以真实旧IpService+隔离Mongo及静态HTTP替代验证cache命中/扁平result/覆盖顺序、null/Decimal与序列化、失败写入顺序；所有外部响应为合成，不调用收费API。缓存miss单次请求/保存，无应用重试。历史非标准JSON/极端数字、真实供应商/网络另验，不安装生产入口。

### S09.i本地验收

- 真实旧IpService+隔离Mongo `IpCacheMappingContractTest`6项与既有IpServiceTest4项，共10passed/0skipped，Checkstyle成功。外部HttpUtil全静态替代，无收费调用；为稳定验证“记录存在即复用”，独享mongod禁用TTL清扫线程，实际回读实体生成的unique ip和createdAt TTL21600秒，不声称实测物理过期删除。
- 完整Python581passed/0skipped/1既有warning，本阶段新增17项，Ruff/500行/空白通过。真实Mongo验证IPv6键/已有记录不刷新、扁平result覆盖ip、对象null省略/数组null与Decimal精度、唯一竞争不覆盖/不重试、写后合成确认异常后下一独立请求读缓存；真实WatchLogs两次lookup只用一次合成供应商请求。
- 新HTTPS transport固定三秒超时、不自动跟随重定向；真实回环服务器验证只发一次且不携带Authorization跳转。无全局代理/网络配置修改。旧服务只取body，适配也不先按HTTP状态替换旧正文解析。上述传输边界尚未由真实供应商/FC网络验证。
- 仅支持已验标准JSON（保留Decimal），旧非标准JSON/极端数字/其他编码和缓存损坏类型仍门槛；未装配业务入口或连接生产，不创建生产索引/缓存。
