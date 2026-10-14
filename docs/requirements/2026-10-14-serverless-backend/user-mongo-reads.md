# S06.b2 用户 Mongo 只读适配器（未装配）

`MongoUserReads`实现现有UserReads端口，接收装配层的惰性database provider和可注入时钟，不创建第二套连接池。每次只对`user`集合find_one，查询maxTimeMS=1000；复用已有连接池的连接/选择服务器/等待超时。没有创建索引、更新字段、迁库或引入ODM。当前未接bootstrap/业务路由，健康与诊断入口保持原状。

按[S06.b1实测矩阵](user-mongo-matrix.md)保留缺失/null区别、读取时刻日期默认、计数默认0、有效24hex ID转ObjectId且不回退String、原token精确查询及数值转换。输出不可变普通UserSnapshot；公开读取仍通过Users清空返回token，不修改记录。驱动/转换错误向上传播，不伪装成不存在或无效token。

## 已验证与限制

- 真实回环独立Mongo：4份旧Java HTTP黄金响应经Mongo→用例→编码逐字段相同，回读记录/文档数量确认未写回；缺失/null、同文本String/ObjectId共存、数字转换和token类型敏感性均与Java矩阵对应。
- 测试只在无凭据、所有种子均回环的standalone Mongo运行，用随机`video-user-read-test-<uuid>`独立数据库，finally删除自己创建的库并关闭客户端。禁止replica set/mongos，未配置连接明确skip，不用mock冒充Mongo通过。
- 日期按集合CodecOptions(tz_aware=True,tzinfo=UTC)解码；普通层不接BSON类型。当前实际覆盖常规String、BSON整型、有限范围Double到Long、十进制整数字符串、Date/ObjectId/null。
- 任意BSON类型并未穷尽：boolean、嵌套文档/数组作为文本或计数、非有限数、超Long范围、非Date日期等未获Java等价证据时抛固定UserMappingError，消息不含记录值；这属于未装配能力的兼容缺口，不能据此为旧业务新增数据校验规则。生产数据类型审计/额外Java样本仍是装配前门槛。
- 未实现完整请求上下文、日志/鉴权拦截器、登录写入、资料/头像用例，也未验证FC与现有Mongo网络。不能称S06或生产API迁移完成。

## 官方依据（2026-10-15查阅）

[PyMongo日期](https://www.mongodb.com/docs/languages/python/pymongo-driver/current/data-formats/dates-and-times/)说明默认返回naive UTC，CodecOptions可显式返回aware UTC；采用集合级选项，不依赖宿主时区。[ObjectId](https://pymongo.readthedocs.io/en/stable/api/bson/objectid.html)提供有效性检查及转换。Spring的ID优先级、构造默认和数字行为依据本仓库真实Java测试，不从PyMongo文档推导。

回退删除此适配器/测试/文档即可，无线上入口/数据/在途任务变化；后续先完成本PR必要CI，再补未验证BSON样本和完整HTTP/日志契约，逐步装配。
