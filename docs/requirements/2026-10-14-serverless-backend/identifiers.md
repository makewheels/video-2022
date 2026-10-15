# S06.i 持久化ID与token生成

## 已核实的旧行为

项目锁定hutool-all5.8.25，已在JDK21上运行该JAR的独立临时探针：固定timestamp
2208988800000、显式node1:1、sequence1/2的输出为3859404870251581441/1442。
当前机器自动节点实际为8:19；javap确认IdUtil调用自动Snowflake构造器，不把其他
版本默认节点描述当本项目事实。Snowflake位布局/epoch参见[官方API](https://plus.hutool.cn/apidocs/cn/hutool/core/lang/Snowflake.html)，本项目精确版本以JAR探针为准。

旧IdService长期ID为本地墙上时间相对2023-01-01的4秒单位+最少5位共享序列+
3位随机数字；使用现有id_counters、longId:<unit>和counter字段。不能把旧
LocalDateTime.toInstant(UTC)顺手改为真实UTC。序列超过99999时扩展而非截断。

## 本阶段实现及明确的适配差异

实现公共Identifiers、共享序列端口和MongoIdSequences。用户/文件等Long ID保留
旧格式及原子$inc。token仍为原Snowflake位布局的十进制字符串，配置node两项
必须与储存适配器一致；本阶段尚未装配生产默认node和时区。

FC不能照搬MAC/PID派生节点+进程内序列。token使用现有id_counters中内部键
tokenSnowflake:<datacenter>:<worker>，仅原有_id/counter字段，一个有界状态文档
持久化timestamp/sequence的打包整数。findOneAndUpdate管线原子取max(当前时间槽,
前值+1)，不同实例/实例重建共享序列。没有新业务字段、集合、TTL或运行时索引。
序列溢出推进逻辑毫秒，小回拨沿用此前逻辑时间；偏差达到2秒失败，不退回内存。
这保留凭据格式与鉴权方式，但不是原Hutool的本地忙等实现。

[Mongo官方findOneAndUpdate](https://www.mongodb.com/docs/manual/reference/method/db.collection.findoneandupdate/)
支持管线及返回更新文档；该能力需要实际数据库支持（Mongo4.2+）。本地8.3.7已验，
CI为7，生产版本/拓扑、写关注和不确定写入仍待S04验。生产切换必须单写者；不允许
旧Java自动节点和新函数并行生成同一节点/时间段的凭据，不把域名回退当状态交接。

## 本次验证与边界

2026-10-15实际6项及架构8项共14passed、Ruff通过：锁定JAR黄金值、Long ID扩展/
当地时区、8实例对象并发80token和40user ID无重复、重建不重复、溢出和回拨、
储存失败不内存降级。仅随机回环隔离Mongo，无生产写入；时钟/随机源仍为测试注入。
下一阶段装配真实时钟、随机源并验证登录HTTP；回退新增模块/测试，无线上配置变化。
