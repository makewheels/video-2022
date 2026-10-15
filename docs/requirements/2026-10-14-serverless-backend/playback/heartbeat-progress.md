# S09.a 旧心跳与进度 HTTP 契约

状态：执行中。目标为迁移前固定旧MVC/真实Mongo行为；不修改业务、不连接生产。

范围：`/heartbeat/add` 与 `/progress/getProgress`，使用隔离库和合成身份。检验状态大小写、匿名身份、登录覆盖、跨设备进度、部分写入及正文绑定失败。旧心跳先保存，再处理进度；不能把失败当作全部回滚。只测试旧接口，不代表Python迁移、播放会话/历史或FC验收完成。

验收：真实Spring MVC转换器/拦截器、Mongo持久化断言；外部服务继承基础测试替代。重点核实仅小写`playing`更新，未知视频不额外校验，按viewer优先/client回退，保留空值与负数行为。异常通过真实非法输入触发，不注入生产故障。

后续：在这些证据上实现普通心跳/进度端口及Mongo适配；两套心跳/播放会话/累计时长/历史仍分别验收。回退仅移除本测试与文档，无生产数据变更。

## 2026-10-16 实测结果

`HeartbeatProgressHttpContractTest`：10passed/0skipped，Maven含Checkstyle成功。真实MVC（非TCP网络）+任务独享回环Mongo，结束后清理全部临时数据与构建副本。

- 只有小写`playing`更新；`PLAYING`、暂停、空串和前导空格仅保存心跳。
- 未登录时正文`viewerId`保留，可跨client按该viewer合并；有token时覆盖为实际用户。旧规则仅记录，不新增鉴权。
- 同viewer跨设备更新保留首个clientId与createTime，但session/进度更新；负进度原样保存，缺失playerTime可清空进度。
- 不要求video存在。身份两项都缺失时HTTP200/code1，但心跳已经保存，不能按事务回滚；非法Long绑定则心跳/进度都不保存。
- GET进度按登录viewer优先；匿名按client查询，不存在返回code0/null，缺少身份返回code1。

没有实现Python心跳写入、播放会话/累计时长或观看历史；未进行生产重放。

## S09.b 进度能力实施边界

独立普通进度用例与Mongo端口：仅小写playing触发，viewer非null优先（含空串），否则client；按旧findOne/save保留读改写语义，不新增唯一索引/原子合并。新文档默认创建/更新时间，旧文档保留video/viewer/client/createTime，只替换session/进度/更新时间；省略null，Long强制BSON int64。

先用旧HTTP对原始BSON固定类型/替换行为，再与真实隔离Mongo对照。没有心跳正文解码/持久化、HTTP装配或播放会话统计；不能单独把本能力接入生产替代整条心跳链路。未知BSON转换保持明确未验门槛。

### S09.b最终本地验证

旧Java HTTP/BSON11passed/0skipped，Maven/Checkstyle成功；完整Python503passed/0skipped/1既有Starlette warning，新增17项（真实Mongo含跨设备、空viewer/client、日期默认/null、整文档替换、删除后save重建、真实写后合成确认异常不重试）。Ruff、500行门禁及diff --check通过。

原始BSON证实ObjectId/Long/Date、null省略、无_class、原创建时间保留和额外字段删除。首次假设_class存在的Java断言失败，查全局MongoConfiguration禁用配置后修正实现并全量复验；未把首轮失败称作通过。其他BSON类型、竞争写入/网络不确定提交和完整HTTP心跳仍未验。
