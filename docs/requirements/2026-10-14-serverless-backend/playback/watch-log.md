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
