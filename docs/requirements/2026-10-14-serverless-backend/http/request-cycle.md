# S06.d5 普通请求生命周期与日志端口（未装配）

新增RequestJournalEntry/RequestJournal普通能力及HTTP层RequestCycle，不接具体数据库、线程局部值或平台协议，不注册任何业务路由。journal.save必须同步完成；具体Mongo日志适配器、日志安全策略和正式路由表仍待。

## 生命周期与旧边界

1. 先resolve一次得到处理器使用的原身份；受保护请求再独立resolve一次做检查，不用第二次替换第一次身份。protected由未来经过审阅的路由配置提供，不能来自用户参数。
2. 拒绝返回旧HTTP401、application/json;charset=UTF-8、code401/请先登录，无data；身份阶段异常使用注入错误映射器。两者不启动日志/处理器/日志时钟。
3. 处理阶段计开始/结束毫秒时刻，处理器异常交错误映射器，再同步save最终HTTP状态。默认仅提供已验证的通用未知错误HTTP200/code1/data null，域专用异常必须另注入完整映射。
4. /file/access、/heartbeat/add按精确原URI跳过日志保存；相似路径不自动排除。耗时为原始end-start，不自行钳零。
5. save抛异常仍保留原响应和已完成业务写，不重放处理器、不自动重试日志；内部结果journal_status区分saved/skipped/failed/not_started，不暴露原异常或凭据。failed只表示未获成功确认，不能断言远端必定没有写入。

身份、时间和日志entry都是调用局部值，没有UserHolder/RequestLogContext或其他共享请求缓存，因此不复制旧ThreadLocal残留缺陷。没有把它宣称为修复现役Java；旧运行行为保持。

## 验证

UserReadGoldenHttpContractTest扩展到11项，真实旧Spring HTTP/隔离Mongo导出401及通用绑定错误两份固定黄金响应，status/contentType/body全部对照；原9份响应不改。Java11passed/Checkstyle通过。

完整Python235passed/0skipped/1既有warning、Ruff/500行/空白通过。新增10项验证两次身份查询、保留首次上下文、save早于return、黄金401/错误、身份提前异常不落日志、处理器异常最终状态、日志失败保留写/响应与后续请求隔离、精确排除及时间差、可注入域映射。架构门禁覆盖新HTTP层不导入环境/SDK/数据库。

S06.d6新增[未装配Mongo适配](mongo-request-journal.md)，已在随机隔离库验证普通元数据日志落库；仍没有将raw token/header持久化到生产。元数据repr隐藏不等于脱敏方案，旧日志安全问题仍须单独处理。

## 尚未通过

现有Application/FC/ASGI入口均未调用本生命周期；具体鉴权路由注册、域异常矩阵、表单/FC元数据、真实日志保存/不确定写入与观测、内部错误映射器/时钟自身失败、请求取消/进程终止等仍未验。动态时钟范围不等于Java每条指令的完全相同计时，未作逐毫秒性能承诺。S06完整HTTP与云端未完成，不能据此开放半成品用户接口。

回退删除未装配模块/测试/夹具/文档，无线上资源或在途数据影响。下一阶段先收尾必要CI，再独立实现日志适配/完整外壳对照或签名能力。
