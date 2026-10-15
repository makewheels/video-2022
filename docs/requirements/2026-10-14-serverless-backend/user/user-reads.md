# S06.a 旧用户读取用例与受控响应对照

范围：实现按旧token读取用户、公开ID读取的纯Python用例、普通值端口和通用HTTP值编码；用真实隔离Mongo/完整Java HTTP生成固定夹具，对照字段、空值、旧token和UTC日期。没有装配Mongo用户适配器、没有增加应用路由、没有FC部署/生产切流，不能称S06登录迁移完成。

业务端口只表达find_by_token/find_by_id，返回不可变UserSnapshot（标准字符串/整数/可空datetime），不接Mongo查询/ObjectId/框架对象。按token保持原始字符串，不trim、不转Bearer/JWT，不新增TTL；None直接未找到，空字符串仍交给端口查询。按ID公开读取仅清空返回快照token，不调用写入、不注销存储凭证。

HTTP值编码保持JavaResult：token不存在为code13/message“登陆token校验未通过”/data=null；ID不存在为code0/message success/data=null；成功13个User字段和空值保留，日期为UTC毫秒Z字符串。端口时间必须明确时区，原Mongo缺失字段/构造默认和驱动转换留下一适配器阶段核实，不把原文档直接灌入公共模型。

黄金夹具来自Git中UserReadGoldenHttpContractTest，仅固定合成用户/手机号/旧token/URL/日期；测试运行于回环临时Mongo，外部服务mock，HTTP请求日志仅在隔离库。可选user.read.fixture.dir只允许系统本任务临时目录，生成后清理；交付到serverless/contracts/user-read的是明确的固定测试产物，不保存真实响应/签名或临时脚本。

下一阶段：Mongo适配器验证集合/ID转换/null与构造默认，再做完整请求上下文/日志/异常及登录写流程。HTTP外壳尚未调用新读取能力，当前业务/接口保持原样。兼容形状通过不等于鉴权/生产网络/FC实测。回退仅删除未装配能力与测试。

实际基线：Java UserController/Service/Result/User来自master fbb8502a；4个固定HTTP响应由上述Git测试导出。导出显式保留Map null并断言回读与实际body相同，不能用省略空字段的样本降低兼容要求。CPython3.12.13完整165passed/Mongo1skipped/既有warning1；JDK21真实隔离Mongo黄金HTTP4passed、Checkstyle/Ruff/500行/空白通过。新PR必要CI待验。
