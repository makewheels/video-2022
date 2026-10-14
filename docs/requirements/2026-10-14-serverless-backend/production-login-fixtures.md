# S05.f 生产判定与验证码 TTL 配置夹具

范围：复用测试环境真实隔离 Mongo/完整 HTTP，以 TestPropertySource 将 EnvironmentService 实际读取的 spring.profiles.active 设为 prod，验证未 mock 的生产判定拒绝111，正确四位码仍可成功。测试数据库及外部服务仍使用既有 test/mocks；不是在真实生产环境调用登录，也不表示生产/FC配置已部署。

TTL验证从实体真实注解解析索引定义，在本次隔离库创建后回读Mongo索引：verification_codes、phone唯一索引和createdAt expireAfterSeconds=600。只证明索引配置/回读，不等待或推断TTL后台删除时间，不改变业务为“精确600秒代码检查”。

固定夹具无真实凭证。源码/构建/dbpath/log均在系统任务临时目录，finally终止本次mongod并清理；真实云服务mock不产生短信/钱包资金操作。通过后更新登录台账，TTL删除时机/并发及故障恢复继续单独验收。回退仅删测试/文档，无生产运行或数据影响。
