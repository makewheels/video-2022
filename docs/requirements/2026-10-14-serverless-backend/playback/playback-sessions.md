# S09.d 播放会话/心跳/退出迁移

状态：实施中。先真实旧MVC/隔离Mongo固定start/heartbeat/exit的JSON响应、BSON和边界，再实施普通端口/业务和Mongo适配。沿用既有播放业务，不改客户端协议或生产入口。

旧规则：start每次创建新会话，用户身份取请求局部；heartbeat查原ID、更新current/max/累计时长/分辨率/计数；exit设置结束时间和任意退出类型但不增加心跳数。保持null/负值、isPlaying忽略、累计时长可回退、退出后心跳、未知ID成功的行为，不引入新权限/幂等规则。

适配复用惰性Mongo连接，有界按ID读取，ObjectId不String回退；Long/Integer/Date、null省略/整文档替换与旧一致。并发仍是旧读改写，不宣称原子统计或崩溃恢复。仅隔离库验收，HTTP解码/生产装配、真实FC网络及全端播放另验。

验收：真实MVC/真实Mongo含start匿名/登录、默认值/重复、心跳回退/null、退出text/plain、无会话、对象缺计数、整数溢出；Python真实Mongo和故障不重试。回退移除未装配模块，无生产数据变更。

## 本地验收（2026-10-16）

- Java真实MVC/独享回环Mongo `PlaybackSessionHttpContractTest` 6passed/0skipped，含Checkstyle。覆盖重复start/默认BSON、token身份/匿名更新、忽略isPlaying/负时长/清空current、text/plain退出/晚心跳、未知与缺失ID、缺counter失败零保存/Integer溢出。
- 完整Python529passed/0skipped/1既有warning，本阶段新增13项；Ruff/500行/空白通过。真实Mongo含生命周期、整文档替换/null max、ObjectId不回退、删除后save重建、实际写入后合成确认异常不重试。后几项基于旧源码并验证新实现，不把每项称为旧HTTP逐项覆盖。
- 非int32旧counter BSON、完整Long/日期JSON转换、真实TCP/FC和并发统计尚未验证；异常映射/HTTP/业务入口未装配。
