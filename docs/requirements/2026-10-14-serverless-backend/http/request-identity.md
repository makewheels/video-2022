# S06.d2 普通请求元数据与身份解析（未装配）

新增RequestMetadata、ResolvedIdentity普通不可变值及IdentityResolver纯用例。RequestMetadata显式保留有序重复header/参数对、原queryString、URL/path/method和remoteAddr；拷贝调用者序列为tuple，避免输入list后续修改污染请求。它不接FC/ASGI/Servlet对象、不解析URL/表单，不把原字典扩充后就宣称保留了多值。

## 旧身份选择

1. header名大小写不敏感，取首个token值；非空字符串（包括空格、Bearer前缀）原样查询，不trim。
2. header不存在或等于空串时，若有精确名token参数则取首值；不找后续有效值，参数名大小写敏感。
3. header为空且无参数时保留空字符串；不存在header也无参数才是None。None不查库，空字符串仍查库，可能匹配旧库空token记录。

最后一点已由真实Java HTTP新增夹具确认：临时库保存空token后，空header/空query能通过受保护接口，完全缺token则401；重复header首值决定结果。这是已存在行为与风险记录，不是在生产创建空凭据。本阶段不擅自改变旧鉴权规则。

IdentityResolver每次返回局部ResolvedIdentity，不缓存身份、不使用进程全局/ThreadLocal，不创建数据库客户端。provider错误直接传播，后续请求无残留；一次resolve只表示旧一次getUserByRequest能力，不能把它当作完成PutToken+CheckToken两次读取的整条鉴权管线。

原headers/参数/query/URL及身份token/user从默认repr隐藏以免调试字符串直接暴露；显式访问仍保留原值。这不是日志脱敏或权限策略，旧Java日志行为未改，Python日志尚未实现。

## 证据与未装配边界

- 真实Java隔离HTTP上下文测试7passed/Checkstyle，含空token边界。
- 完整Python217passed/0skipped/1既有warning、Ruff/500行/空白通过；多值/首值/空格/空token/异常后请求/序列快照/repr验证，40个请求在4个测试线程共享resolver且身份不串；此为纯用例并发，不代表FC并发验收。
- 真正的隔离Mongo组合验证空token/普通token读取、错误header覆盖正确query、重复参数取首值及记录数量不变。
- 现有http.Request及FC/ASGI外壳保持原状；它们尚未提供可靠的Servlet等价多值/表单合并/原URL/peer元数据。未来外壳必须由原始协议采集，不能从已折叠字典恢复丢失值。

没有路由、401响应门槛、同步日志或完整异常装配，S06完整HTTP仍未通过；不因存在该能力而启用用户接口。后续补外壳多值转换/同步日志/故障清理与对照，另行处理真实OSS签名。回退只删除未装配模块/测试/文档，无线上入口/数据或在途任务影响。
