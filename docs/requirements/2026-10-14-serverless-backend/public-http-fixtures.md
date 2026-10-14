# S05.c 公共列表受控 HTTP 夹具

先实现 Java 既有 Controller/Service/Repository/拦截器的 HTTP 集成测试，不实现 Python 业务。继承 BaseIntegrationTest，真实隔离 Mongo；云外部服务沿用既有 Mockito mock，不能请求生产 OSS/FC。MockMvc 从完整 WebApplicationContext 装配，包含真实参数绑定、Result 序列化和请求/异常日志。

夹具仅固定测试 ID/标题/手机号/历史链接/日期，无真实个人资料；测试在独立 test 数据库，清理全部本测试集合，不触碰已有开发/生产库。本地启动 mongod 使用随机回环端口、系统任务临时 dbpath；依赖复用既有 Maven 本地仓库，源码与 target 中转在系统临时目录，finally 终止本次进程并清理。

验证目标：匿名成功、缺失/非整数参数 HTTP/body、limit=0、负 skip、字面关键词/空白、PUBLIC 非 READY、PRIVATE/UNLISTED 排除、分页及 total、缺用户/封面/嵌套对象、旧 token、日期编码与历史链接、成功/异常日志确实存在。先运行得到实际证据再更新台账，不能为了让测试绿改变业务或猜定未知响应。

本阶段固定夹具结果仅证明既有 Java 行为，为下一阶段 Python 对照提供基线；不等于 FC/Mongo 生产网络、线上签名、播放器或完整 S05/S07 验收。实际命令和 passed/failed 见 verification.md。回退只删测试/台账，不影响业务运行。
