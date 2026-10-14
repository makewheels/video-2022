# S06.d6 同步 Mongo 请求日志适配（未装配）

`MongoRequestJournal` 实现普通 `RequestJournal` 端口，复用注入的惰性 database provider。每个新日志一次 insert_one，调用层不加重试、不重放业务；原驱动配置没有修改。未接入现有 Application/ASGI/FC 或任何生产业务路由。

## 与旧真实 BSON 对照

旧 RequestContextHttpContractTest 真实 Spring/隔离 Mongo 已核实：requestLog 集合、ObjectId 自动 ID、无 _class、Date 开始/结束时间、Long timeCost、Integer HTTP 状态；未采集的 request.body/response.body/response.headerMap 是省略字段而非显式 BSON null。headerMap 使用首个大小写不敏感 header 的首值并保留首个名称拼写。

新适配保持这些形状，毫秒时间通过 UTC epoch+timedelta 计算而非浮点转换；timeCost 用 BSON Int64，原始 end-start 不钳零。缺失 query/user-agent 省略，空 query 保留空串。URL 来自已审阅元数据，不在此推测代理地址或补 query。requestPath 与 request.path 相同。

旧日志保存原始 header/query（包括 token）的行为在本地仅以固定合成值验证，未擅自改日志规则；repr 隐藏不等于脱敏。这里没有读取生产日志，也没有把真实凭据写入测试或报告。日志策略须另行审阅。

## 失败和未验范围

RequestCycle 同步调用适配器；日志异常仍保留原响应/已完成业务写，内部只返回固定 failed 状态。失败可能已落库但未收到确认，不宣称零写入，也不重试业务。测试的 provider 抛错仅故障注入，不是真实网络不确定写入。

S06.d7真实旧HTTP/隔离Mongo已验证：含点header（X.Fixture）使日志保存失败，业务资料更新与HTTP200/code0保留、requestLog为0且旧RequestLogContext残留；此项没有注入Mockito异常。$fixture header可以原名落库并正常清理上下文。Python保留含点名的固定JournalMappingError，并由生命周期转为failed，保持业务成功而不落日志；不把它改为业务400/500。新Python没有ThreadLocal，因此不复制旧上下文残留。其他特殊键仍未穷举，不能宣称全部header兼容。日期超范围、驱动网络重试/写关注、平台元数据、完整 HTTP 管线/业务异常仍待；S06 尚未完成。

回退删除本模块/测试/文档，无生产资源和日志回退。
