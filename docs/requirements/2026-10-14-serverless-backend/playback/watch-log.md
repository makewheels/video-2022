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
