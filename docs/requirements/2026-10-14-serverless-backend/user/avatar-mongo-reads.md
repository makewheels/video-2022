# S06.c3 头像文件 Mongo 只读适配器（未装配）

MongoAvatarFiles实现AvatarFiles，复用传入的惰性database provider，不建第二个客户端/池。对物理`file`集合单次find_one，maxTimeMS=1000，不投影/改写记录、不建索引、不请求OSS；仅返回普通key字符串或None，BSON类型不进入公共用例。

## 旧语义与实测

| 原始数据/查询 | Java基线与Python |
|---|---|
| 有效24hex fileId | ObjectId查询；没有则None，不回退同文本String键 |
| RAW_VIDEO、其他owner、deleted=true | 原findById仍读取，不增加过滤 |
| 缺记录、key缺失/null | None，用例回退旧URL |
| key为空串 | 原样空串，用例回退旧URL |
| key为空格 | 保留空格，不trim；用例仍请求签名 |
| key为BSON整数1234 | 转字符串"1234" |

UserMongoReadContractTest新增2项，连同原3项真实隔离Java Mongo基线共5passed/Checkstyle通过。Python实际Mongo验证相同样本，并将原4份profile/channel黄金组合测试替换为真实user+file集合读取，仅URL签名使用固定替代端口；完整201passed/0skipped/1既有warning、Ruff/500行/空白通过。

测试仍只允许无凭据回环standalone Mongo，随机独立数据库finally删除，逐测试清理自己库内user/file集合；临时环境/缓存/target/dbpath/log和mongod进程均finally清理。没有生产文件或真实OSS调用。

## 未验边界

其他BSON key类型固定AvatarFileMappingError不含值；不是新增生产限制。旧Spring会转换整个File，Python当前只解释所需key，其他字段的异常类型及所有转换尚未穷尽，仍是装配前兼容门槛。普通读取不新增owner/type/deleted限制是保持源码行为，不把它等同于头像上传写权限规则。

当前没有装配到bootstrap/HTTP，没有实际OSS签名适配、请求上下文/日志/异常完整矩阵、头像写入或FC网络证明；S06未完成。匿名头像路由旧缺口仍仅记录未修。回退删除未装配能力/测试/文档，无线上入口或数据变化。下一阶段先收尾必要CI，再独立推进签名适配或完整HTTP上下文契约。
