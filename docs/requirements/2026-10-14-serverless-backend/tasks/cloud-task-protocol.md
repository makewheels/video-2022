# M033 旧云任务提交、回调及任务BSON契约

[固定台账](../overview/remaining-work.md) · [受理/恢复设计](task-acceptance-recovery.md) · [已有提交故障](task-submission-persistence.md)

状态：**已合并[PR #234](https://github.com/makewheels/video-2022/pull/234)**，2026-10-30 15:38:53正常合并为`4ac865867a7d74847d7bc5686ae7744f57f9b783`。最终head `db7bfe4432db64315e6254ec0f3f4f2e8a47b933`的[CI41020464721](https://github.com/makewheels/video-2022/actions/runs/41020464721)实际12成功/2条件E2E跳过、Java787passed/0skipped；交付163组真实旧协议/完整任务BSON契约，本机相关Java23项通过。本批只固定旧Java协议，不提前实现M034–M038、不改变生产任务所有者或发起生产重放。

## 契约层次与边界

- CPU/GPU使用真实CloudFunctionClient/FcTaskSubmitter/Repository及独享Mongo，只把函数URL指向本机HTTP服务器。记录真实UTF8 POST、Async/密钥header是否匹配、Content-Length、JSON和发送时完整任务/Transcode状态；模拟202/429/拒绝/5xx、响应中断和重定向，验证不隐式再发送。
- 自建回调使用真实Spring MVC、控制器、FcTaskCallbackService和Mongo仓储。规划、具体媒体登记及最终READY副作用是**显式mock边界**，记录调用和调用时数据库状态；因此本契约固定身份/attempt/manifest分派/租约/返回，不冒充M035/M036的完整媒体登记或就绪验收。
- legacy云函数通过真实实现及原MVC回调固定JobId、先调用后保存、重复及部分失败；旧HTTP发送服务只在Hutool网络边界mock，绝不访问代码中的真实函数地址。
- legacy MPS使用真实实现、mock SDK及既有模型响应；提交后的旧后台循环只截取调度边界，不创建无法收尾的线程/秒级sleep。MPS兜底/活跃检查以真实fallback方法和mock SDK固定名额、job、deadline及查询租约；不声称已经迁移M037。
- 所有业务ID、对象key、配置和响应均为合成数据；旧LOCAL登记已由M027独立固定，不重复计算，真实云端/冷热/设备/计费与进程崩溃仍未验。

## 固定文件与无损比较

语料位于`serverless/contracts/cloud-task-protocol/`：[submission.json](../../../../serverless/contracts/cloud-task-protocol/submission.json)60组、[callback.json](../../../../serverless/contracts/cloud-task-protocol/callback.json)69组、[legacy.json](../../../../serverless/contracts/cloud-task-protocol/legacy.json)34组，共163组、各文件编号唯一。由真实旧方法/MVC/回环HTTP执行后生成，再撤去生成参数从固定文件全文重放，不是手写预期BSON。

每组保留输入、HTTP/返回或显式异常类别、所有相关业务集合的完整前后文档及外部/登记调用顺序。BSON Integer/Long/Double/Date和ObjectId用带类型标记表示，保留未知字段、缺失/null和整保存/窄写差别；诊断requestLog/exceptionLog另记数量，不将不稳定堆栈当任务业务字段。

只对不可复现值做有界归一：运行时日期必须落在实际调用起止加既有超时/退避/租约窗口；下一attempt先验证原前缀和6字符后缀，legacy Snowflake先验证真实生成形状。消息/业务字段不任意排序或清空，调用数组保留真实顺序；归一不能掩盖deadline、计数或类型变化。

## 必须保留的旧行为

- SUBMITTED先持久化再发付费HTTP；TRANSCODE输出key先窄写。确认只更新仍同attempt/SUBMITTED的requestId，不覆盖快速完成。旧attempt或忙/终态不再次发送，429不增业务attempt，未知受理等待原deadline。
- 回调按缺字段→任务→attempt→终态/兜底→status/manifest路径顺序短路；provider以存量任务为准，不能信任回调body。manifest的schemaVersion及身份按旧Fastjson getter，不增加新媒体/参数验证。
- 合法回调先保存PROCESSING/manifest/5分钟租约，再分派PROBE/TRANSCODE/COVER；RegistrationException保留登记恢复，其他旧失败按原有限attempt/退避/兜底。重复/晚回调不覆盖终态；不能把登记重试变成付费重编码。
- 当前回调403空体及200/400/404搭配Result正文按旧MVC固定，坏JSON等走既有错误处理；不擅自改422/503或把HTTP200单独当成功。legacy回调不凭新协议假设变成幂等。
- MPS已有job只查询；不确定提交不能重耗fallbackCount。旧租约/30分钟期限、finally释放及失败残留均按完整BSON记录，不能只比较最后的status。

## 验证与回退

本机JDK21.0.10/Mongo8.3.7，只用系统临时目录的独享`127.0.0.1:13905/video_m033_d00439ce_test`，显式覆盖全部`spring.mongodb.uri/host/port/database`，不接27017；HTTP只监听回环随机端口，测试finally关闭。三个新JUnit方法先实际生成，固定多键输入为有序映射避免JDK不可变Map随机顺序，再重生成受影响回调语料；没有为此改业务。

撤掉生成参数后，三个新契约及FcTaskFlowIntegrationTest/FcSubmissionPersistenceTest/MpsFallbackRecoveryTest/CoverTaskSubmissionTest七类**一次合跑23passed/0failures/0errors/0skipped**，Checkstyle零违规，15:19结束。新JUnit方法是3个、固定组数是163、相关回归是23项，不能混成三个新增业务能力或163个JUnit方法。Python无运行时/测试改动，本机未重跑其全量。

实际源证据包括六组中断响应每次仅一个POST、重定向不跟随、快速完成不被requestId覆盖；登记异常保留PROCESSING且重复HTTP不重复分派；schemaVersion Long回绕沿旧getter；legacy重复回调仍分派两次；MPS重复失败仅提交一次、未知受理转原失败出口、已有租约不查询。完整媒体登记/规划已明确mock，不能借这些事实宣称READY或真实收费恢复已验。

无运行时/依赖/云配置/业务字段或索引变更；回退仅撤测试、合成语料及文档，不恢复业务数据库、不影响在途任务。M033完成只解锁后续对照依据，不代表Python提交/回调已迁移或可靠任务已上线。
