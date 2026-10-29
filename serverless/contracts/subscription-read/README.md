# S15.b.1 订阅读取旧契约

`SubscriptionReadHttpContractTest` 使用真实 Spring MVC 和独立本机 Mongo 导出 `cases.json`。全部数据合成；共享 before/after 保存完整用户/订阅 BSON，每个请求实际验证业务文档不变，并保存响应、状态、Content-Type 和请求/异常日志计数。

在 server 下运行测试，`-Dcontract.subscription-read.output.dir=<系统临时目录>` 可生成；不传则重放固定文件。必须显式指定隔离 Mongo。固定日期保留毫秒值，ObjectId 用 `$oid` 标签保留与字符串引用的区别。Python 使用同一数据逐项对照，不将只读业务与日志写入混淆，不宣称真实 FC 或实机已验。
