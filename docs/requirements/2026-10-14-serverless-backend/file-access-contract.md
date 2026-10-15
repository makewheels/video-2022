# 文件重定向与异步计费旧契约

范围：完整Spring MVC管线、真实随机回环Mongo、合成签名与mock OSS，固化/file/access的302、参数/时间/HMAC先后及异步日志实际结果。没有真实TCP/OSS/生产日志审计。

先记录旧行为，再实现Python重定向与可靠计费。异步方法依赖RequestUtil线程上下文是源码疑点，须完整文档样本和实际异常观察，不能用注入异常替代或擅自更改扣费规则。spy只捕获线程/上下文和真实方法异常并原样抛出，latch确认结束后清库。可靠任务/计费留在独立阶段，不将CompletableFuture移植为FC后台线程。

状态：JDK21/完整Spring MVC/真实回环随机Mongo，9项通过、0失败/错误/跳过，Checkstyle通过。

实际结果：302带application/json成功正文（code0/data:null/message:success），OSS预签名TTL3小时。完整文档下真实异步线程没有RequestContextHolder，saveAccessLog在取IP时抛IllegalStateException，fileAccessLog/ossAccessFee均0；请求仍302。缺文件先调度异步工作再返回code31，异步NPE。缺参code1；过期/篡改code35，对外message为通用“文件访问签名无效”，过期详情仅exceptionLog。首次“过期详情直接外返”假设被测试推翻并修正；最终完整复验通过。

spy不制造故障、真实方法异常原样抛回CompletableFuture，latch确认结束后清库。没有复制该缺口到Python或顺带启用收费。构建/数据库/log均系统临时目录，finally停止Mongo并清理。

回退只删除新增测试与文档，不改运行代码。所有请求均不得写普通requestLog（旧排除路径）。
