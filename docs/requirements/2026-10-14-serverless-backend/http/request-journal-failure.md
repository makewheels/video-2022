# S06.d4 请求日志故障语义基线

RequestJournalFailureHttpContractTest使用真实Spring/MockMvc、真实隔离Mongo及MockitoSpyBean，仅对指定MongoTemplate操作注入固定异常；不是制造或宣称真实Mongo网络故障。OSS等外部依赖仍沿用BaseIntegrationTest mock，无生产请求/日志/真实凭据。

## 实际结果

| 注入点 | HTTP/业务结果 | 日志/上下文 |
|---|---|---|
| afterCompletion的save(RequestLog)失败，读取本人资料 | HTTP200/code0，资料照常返回 | 无日志文档；UserHolder清空，但RequestLogContext残留已填endTime/httpStatus的记录 |
| 同线程接着无效token请求 | HTTP401/code401 | 未进入日志拦截器，前一RequestLogContext仍为同一对象；UserHolder仍空 |
| 成功更新资料后日志save失败 | HTTP200/code0，昵称已经写入真实隔离Mongo | 没有回滚业务写；无日志文档，日志上下文仍残留 |
| 身份查询findOne(User)在PutToken阶段失败 | 已处理异常HTTP200/code1/未知错误 | 未进入日志阶段、无日志文档；两个上下文均空 |

这些结果来自真实框架调用链，不从方法会throws推断HTTP一定500。RequestLogInterceptor的清理位于save之后，失败时不能称已完成日志清理；失败被afterCompletion调用链处理后，其他身份清理仍发生。

## 验证边界与后续要求

3项测试/Checkstyle/500行/空白通过。数据、token和错误消息都是固定合成值。afterEach明确移除两个ThreadLocal并清理隔离库，既有缺陷不会污染其他测试；spy只拦截目标方法，其他业务写入/读取实际执行Mongo。

这是指定方法故障注入，不是网络断连、write concern不确定成功、超时重试、真实生产日志或异步并发验收。没有修改Java/Python运行行为，也未修复旧残留问题。

Python装配需保留接口/业务提交边界，不能未经证据把日志失败改成业务500或重试资料写入；应采用请求局部值/可靠finally清理，避免照搬ThreadLocal泄漏。旧日志保存raw header/query的安全问题仍待独立评审，不把本阶段当成脱敏实现。完整故障观测/同步日志适配、异常映射及FC网络仍未验，S06未完成。

回退删除测试/文档，无在线入口、资源、在途任务或用户数据变化。下一阶段先收尾必要CI，再依据该基线设计普通日志端口/生命周期，而不是直接打开半成品业务路由。
