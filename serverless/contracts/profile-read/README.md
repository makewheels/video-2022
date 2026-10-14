# Java HTTP 资料/频道黄金响应

5份固定合成样本由UserReadGoldenHttpContractTest实际请求旧Spring HTTP管线生成，旧运行代码基于master f98e91e2，测试扩展源码与本目录一同提交。JDK21/UTC、真实隔离Mongo、OSS签名mock为example.invalid固定值；没有生产数据或真实凭证。

profile/channel各签名成功与缺文件回退，另有不存在频道。导出器复用user.read.fixture.dir，仅接受系统任务临时目录内video-user-read-*父目录的goldens子目录；保留null并重解析验证。持久文件只作格式化，不改值。生成测试9passed/Checkstyle通过；Python逐字段对照及真实Mongo用户读取组合通过，头像文件/签名仍为替代端口。

HTTP Content-Type/状态、字段类型/null/日期均参加对照，无新增归一化或忽略项；isSubscribed仅加入固定安全字段名表用于差异路径，字段值仍比较。
