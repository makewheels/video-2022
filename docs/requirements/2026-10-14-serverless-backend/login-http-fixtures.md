# S05.e 登录与观看身份受控 HTTP 夹具

实现前范围：在既有 BaseIntegrationTest 中使用完整 WebApplicationContext/MockMvc 和真实独立 Mongo，外部服务 mock。验证验证码请求复用/失败/成功、非生产111必须先有记录、token覆盖/旧凭据、公开用户返回脱敏但数据库不注销、登录必需接口401、钱包保留、client/session每请求持久化IP/UA。

固定测试用户/验证码/token/IP/UA，不使用真实凭据。进程、dbpath、源码与构建中转均用系统任务临时目录，finally清理。验证码TTL后台删除/生产环境配置、并发登录和钱包事务另行验证，本阶段不能用mock或立即删库替代它们。iOS路径差异仅列台账，不在测试中添加旧实现不存在的接口。

实际结果再填 verification/progress；不预先假称通过。回退只移除测试和文档，不变更业务/生产资源。
