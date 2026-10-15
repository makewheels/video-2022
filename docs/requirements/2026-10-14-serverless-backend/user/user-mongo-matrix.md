# S06.b1 旧 Mongo 用户读取矩阵

先调查后适配：现有Java MongoMappingConverter会构造User后填字段，PyMongo直接字典不是等价读取。用Git测试、真实隔离Mongo验证物理集合、缺失/null日期及计数、String/ObjectId键查询和常见数字转换；本阶段只输出实际结果，不猜转换规则或添加业务限制。

固定原始BSON样本，无实际个人记录；继承既有BaseIntegrationTest，独立回环临时mongod，源码/target/log/dbpath均系统任务临时目录并finally清理。直接现有UserRepository读取，不改Controller/Service/数据模型。

## 实测矩阵（Java，2026-10-15 03:05）

| 原始 BSON / 查询 | 当前 Java 结果 |
|---|---|
| 物理集合 | `user` |
| createTime/updateTime/subscriberCount/videoCount 缺失 | 保留构造器默认：读取时刻日期、计数0 |
| 上述字段显式 null | 四字段均为 null，不保留构造器默认 |
| phone 缺失 | null |
| 24位有效十六进制字符串作 ID 参数 | 转 ObjectId 查询；优先 ObjectId 行，没有时不回退同文本 String 行 |
| token 查询匹配 String ID 行 | 可读，实体 id 为字符串；与同文本 ObjectId 行可共存 |
| phone BSON Long 0 | Java String "0" |
| subscriberCount BSON Double 12.75 | Java Long 12 |
| videoCount BSON String "7" | Java Long 7 |
| BSON Date | 保留毫秒值 |
| token BSON Long 1234，查询 String "1234" | 无匹配；直接按ID读取该行后 token 转 String "1234" |

隔离 Mongo 3 项集成测试全部通过，Checkstyle通过。第一次草稿误认为显式null保留默认，实际失败；用逐样本标记核实缺失保留/显式null清空后修正契约断言，没有改旧后端行为。全部样本为固定合成数据，无生产连接。读取时刻默认日期是既有构造器行为，不写回数据库；Python不能把缺失与null统一用dict.get处理。

供S06.b2 Python只读适配器实施。没有生产数据审计、FC/Mongo网络或Python适配器通过证明；其他BSON类型/并发/完整请求日志仍后续验证。回退只删除测试/文档，无运行资源和生产数据影响。

## S06.c3 头像File读取补充

同一真实Java基线新增2项：物理集合file，String @Id的24hex转ObjectId且不回退；RAW_VIDEO/其他owner/deleted=true不被findById过滤；key缺失/null保留null，空串/空白保留，BSON Long1234转String。连同原3项共5passed，详见[头像Mongo读取](avatar-mongo-reads.md)。其他File字段转换和异常类型尚未穷尽。
