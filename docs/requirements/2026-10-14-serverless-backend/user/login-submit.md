# S06.g2 验证码提交与登录持久化

基于S06.g1，按旧UserService、User构造器、WalletService和已有Java登录HTTP夹具
实现验证码提交能力。错误为12（无记录）/11（不匹配）；非生产111仅在有记录时
允许。生产标志必须由后续装配按旧prod配置映射，不能用Python debug推断。

成功保持删码→读/建用户→换token→保存用户→保留/创建钱包的顺序；旧用户的
资料和时间不随登录更新。新用户计数为0，钱包余额为Decimal128零；沿用Mongo
集合和字段。MongoConfiguration关闭_class，自定义金额转换为Decimal128；
Long计数以BSON Int64保存，合法十六进制String ID保持ObjectId映射。

新增隔离Mongo验证：缺码/错码/生产禁止111无写入、正确码和非生产111创建用户
及钱包、旧token失效且原钱包余额/资料/时间不变、用户写入失败和钱包写入失败
分别留下旧顺序对应的部分状态，并向调用方传播异常。

## 未完成边界

用户ID和token通过注入生成器提供，测试为固定合成值；不是已实现旧IdService或
Hutool Snowflake跨实例生成。生产HTTP入口未注册、环境配置未装配，不对生产
登录或短信发请求。Java已有夹具本次未重跑；源码对应和Python隔离测试不能
替代Java/Python全HTTP动态对照。

这仍是旧的非事务过程：删码后崩溃会消耗验证码，钱包步骤失败可能留下已换token
的用户。没有新增事务、唯一索引、自动重试或恢复语义；完整登录验收必须继续核对
并发/故障恢复和生成器，再装配请求身份/同步日志/绑定/响应，不能宣称S06完成。

## 回退与下一阶段

本次实际验证（2026-10-15）：Python3.12.12、临时回环MongoDB8.3.7、web及deployment
依赖组。serverless回归308passed、2deselected、1既有Starlette warning；Ruff通过。
两项既有符号链接测试因本机Windows权限明确排除，不能称全套通过；Linux CI待验。
临时数据库进程、依赖环境、缓存、pytest目录均置于任务临时目录并finally清理。

本阶段删除新增login模块及login_response即可回退，无线上资源或数据变更。
下一阶段完成ID/token生成与隔离用户HTTP组合，再补client/session和头像上传；
云端发布身份及FC→Mongo网络验证仍独立阻塞。
