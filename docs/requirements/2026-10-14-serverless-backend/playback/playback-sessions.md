# S09.d 播放会话/心跳/退出迁移

状态：实施中。先真实旧MVC/隔离Mongo固定start/heartbeat/exit的JSON响应、BSON和边界，再实施普通端口/业务和Mongo适配。沿用既有播放业务，不改客户端协议或生产入口。

旧规则：start每次创建新会话，用户身份取请求局部；heartbeat查原ID、更新current/max/累计时长/分辨率/计数；exit设置结束时间和任意退出类型但不增加心跳数。保持null/负值、isPlaying忽略、累计时长可回退、退出后心跳、未知ID成功的行为，不引入新权限/幂等规则。

适配复用惰性Mongo连接，有界按ID读取，ObjectId不String回退；Long/Integer/Date、null省略/整文档替换与旧一致。并发仍是旧读改写，不宣称原子统计或崩溃恢复。仅隔离库验收，HTTP解码/生产装配、真实FC网络及全端播放另验。

验收：真实MVC/真实Mongo含start匿名/登录、默认值/重复、心跳回退/null、退出text/plain、无会话、对象缺计数、整数溢出；Python真实Mongo和故障不重试。回退移除未装配模块，无生产数据变更。

## 本地验收（2026-10-16）

- Java真实MVC/独享回环Mongo `PlaybackSessionHttpContractTest` 6passed/0skipped，含Checkstyle。覆盖重复start/默认BSON、token身份/匿名更新、忽略isPlaying/负时长/清空current、text/plain退出/晚心跳、未知与缺失ID、缺counter失败零保存/Integer溢出。
- 完整Python529passed/0skipped/1既有warning，本阶段新增13项；Ruff/500行/空白通过。真实Mongo含生命周期、整文档替换/null max、ObjectId不回退、删除后save重建、实际写入后合成确认异常不重试。后几项基于旧源码并验证新实现，不把每项称为旧HTTP逐项覆盖。
- 非int32旧counter BSON、完整Long/日期JSON转换、真实TCP/FC和并发统计尚未验证；异常映射/HTTP/业务入口未装配。

## S09.l（12/14）会话JSON与HTTP

显式`PlaybackApplication`组合旧start/heartbeat/exit与已有普通会话用例/共享身份和同步日志，不安装现役Backend/FC入口。先用真实旧MVC/隔离Mongo导出108组请求/完整响应/BSON/日志数量样本，Java默认执行复核固定快照，导出必须显式启用。

- start/heartbeat保留Spring逐字段JSON绑定：数字词法转String、Long/Boolean转换、未知字段忽略/大小写敏感、重复字段逐次校验；exit保留Fastjson单引号/无引号字段、注释/多余逗号、字段smart-match、布尔转Long、指数/对象/数组转String。两条解码路径独立，不统一成更严格JSON。
- GET/PATCH不匹配以及exit不接受的媒体类型在身份/请求日志之前拒绝，仍写异常日志；start/heartbeat的text/plain在绑定期拒绝并保留requestLog。application/problem+json仅前两条接受，exit仅application/json和text/plain。都保持旧HTTP200/code1错误格式。
- 请求局部登录身份仅在start写userId；匿名仍可更新原会话，退出后心跳保留endTime和exitType。时长覆盖/null/负值、指定ID、未知ID成功、计数与整文档替换复用已验能力，不新增所有权或幂等规则；requestLog失败不重复start。
- 已读Web VideoPlayer：正常exit走JSON，关闭/隐藏路径用`sendBeacon`+application/json Blob；CLI也发送JSON。独立headless Chromium→真实回环ASGI→任务独享Mongo实际发送JSON和text/plain两种Blob均成功，2会话/4请求日志/0异常，进度/时长/退出类型/清晰度落库正确。实际Blob媒体类型为application/json或text/plain，无自动charset；首次预设charset的探测断言被实测纠正后复验通过。

浏览器验证是独立合成页的真实Beacon传输，不是完整VideoPlayer、关闭标签时机或移动端验收；不把源码调用盘点说成各端已跑通。全部Fastjson扩展/任意复杂对象String序列化顺序/UTF-16或32/旧异常BSON类型和FC仍另验。实现不执行JSON类型加载或求值；公共业务/端口不引入解析器/框架/数据库类型。回退本阶段提交即可，生产数据/配置未改。

本地实际完整Python754passed/2deselected（Windows符号链接权限）/0skipped/1既有warning；Java7passed/0skipped（既有6项+108组矩阵1项）、Checkstyle成功，Ruff/架构/500行/空白通过。Java默认模式已核对固定样本，未仅执行导出。必要CI按当前提交另验。
