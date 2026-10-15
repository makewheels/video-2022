# S06.g1 验证码申请

按 UserService.requestVerificationCode 和已有 LoginHttpContractTest 的契约实现：查询
verification_codes.phone；存在即返回，不生成新码、不更新 createdAt；不存在才保存
phone/code/createdAt。业务层注入生成器和时钟，Mongo 适配器只做实际查询和插入。

不新增手机号校验、短信发送、精确过期判断、重试或 upsert。运行时不创建索引；
phone 唯一索引和 createdAt 600 秒 TTL 仍需部署阶段核对。并发查询后的插入冲突
透传，不能称为原子申请或完成并发恢复。日志不记录手机号或验证码。

新增真实隔离 Mongo 测试验证字段、重复申请不刷新、过期但尚未被 TTL 删除的记录
仍复用、唯一冲突不覆盖原记录；另验证写失败不返回成功。

当前没有注册生产路由，也没有装配实际随机生成器；完整登录还缺提交验证码、
token 生成、用户及钱包写入与故障恢复。下一阶段继续这些能力，随后统一装配
用户 HTTP 入口，避免把半成品暴露到生产。

回退删除本阶段新增模块和测试即可；没有线上资源、配置或业务数据变更。

## 本次实际验证（2026-10-15）

Python 3.12.12 / 本机临时 MongoDB 8.3.7：新增4项与架构边界8项共12 passed；
Ruff全目录通过。完整serverless回归270 passed、3 skipped、2 failed、1既有warning。
失败是既有release测试创建符号链接触发Windows WinError1314；跳过是两个发布SDK
测试模块未安装deployment组，以及原型读库测试未配置MONGODB_DATABASE。不能报告
全套通过。Java既有夹具本次未重跑，FC网络/TTL后台删除/生产HTTP均未确认。
