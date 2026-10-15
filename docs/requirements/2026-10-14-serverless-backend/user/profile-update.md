# S06.e1 资料更新用例与Mongo适配（未装配）

ProfileUpdates只依赖ProfileWrites和注入时钟；MongoProfileWrites复用传入的惰性database provider，对旧user集合单次update_one($set)，不自建客户端、不接入bootstrap/路由。不创建新字段、索引、用户或重试任务。

## 保持的旧行为

- 先nickname后bio，非null才检查Java UTF-16 code unit长度上限30/200；不trim、不按Python len或可见字符重新解释限制。用UTF-16LE字节数除2计数，surrogatepass避免在长度层增加额外Unicode拒绝；未穷尽孤立代理码点的JSON/数据库编码行为。
- 合法值包括None、空串、空白；两个字段总是一起传入写端口，None明确写BSON null，不是PATCH。调用者负责把已经认证的用户ID传入，本用例不是鉴权入口。
- 仅$set nickname/bio/updateTime，不改token、avatar、createTime或计数；超长不读时钟、不写库。业务provider异常向上传播，不做应用级重放。
- 有效24hex用户ID转ObjectId，不回退同文本String记录；upsert=False。零匹配保持旧void更新语义，不擅自新增“用户不存在”错误或插入用户。
- HTTP值编码保留旧200/application/json、code0/success或code1/原错误消息，data均null。实际路由/JSON到DTO的转换、鉴权/日志仍未装配。

## 官方依据与证据

[Python3.12 codecs](https://docs.python.org/3.12/library/codecs.html#error-handlers)支持UTF-16与surrogatepass；[PyMongo更新](https://www.mongodb.com/docs/languages/python/pymongo-driver/current/crud/update/)提供update_one/$set/upsert控制。旧字段/长度/零匹配规则来自仓库UserService/UserRepository和真实Java基线，不从驱动文档推导业务规则。

Java真实隔离HTTP黄金14项、原始Mongo更新基线6项合计20passed/Checkstyle；新增3份固定资料更新Result夹具。完整Python247passed/0skipped/1既有warning、Ruff/500行/空白通过，含3份响应对照、边界与真实Mongo更新/清空/仅改三字段/ObjectId不回退/零匹配不upsert/拒绝不变文档。

所有写入仅在无凭据回环standalone的随机独立测试库；临时env/cache/target/log/dbpath及mongod均finally清理。没有生产用户写入、新旧双写或调用OSS。

## 剩余与回退

输入类型强制转换、缺失/显式null绑定、非法JSON/代理码点、完整路由鉴权/同步日志、网络不确定写入/驱动重试配置及真实FC均未完成；不能直接开放updateProfile。适配器使用既有连接池超时，无新增应用重试，不把异常简单当作写入没有发生。旧空token/头像/日志问题仍仅记录。

回退删除未装配能力/测试/夹具/文档，无生产资源或在途数据影响；下一阶段先收尾必要CI，再推进完整外壳/用户路由隔离验收或真实签名/日志适配。
