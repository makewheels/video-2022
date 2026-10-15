# S09.e 观看历史读取/清理

状态：实施中。固定旧真实MVC/隔离Mongo的用户身份、重复日志/缺失视频、分页/封面、清理范围，再迁入普通端口和Mongo适配。仅隔离测试，不在生产重放清理。

保持按viewer相等筛选（含null匹配缺失）、createTime倒序、page*pageSize的Java int溢出与旧skip/limit处理；先分页再批量读视频，保留重复日志与已删除视频的空标题/封面。封面按两小时签名；无新可见性过滤/登录规则。历史接口旧CheckToken未登记，匿名读取/清理空viewer仅记录，不顺手改权限。

业务值/端口不依赖BSON/框架；Mongo复用惰性provider，按字段有界查询，clear只有一次deleteMany，不重试。HTTP/JSON装配、watchLog创建/计数与真实OSS/FC另验。

## 本地验收（2026-10-16）

Java真实MVC/独享Mongo5passed/0skipped、Checkstyle成功：重复日志/私密标题/已删视频空字段、逐行两小时签名、默认分页、0/-1 limit无限、负skip和int溢出、匿名null/缺失viewer读取与清理、登录清理只影响本人，24hex视频/封面无String回退。

完整Python541passed/0skipped/1既有warning，新增12项（实际Mongo，仅签名替代），Ruff/500行/空白通过。clear实际删除后合成确认故障仅调用一次；签名失败无历史写入/重试。其他旧BSON/完整实体异常映射未验；投影只映射本能力所需字段，不声称兼容损坏的其他Video字段。未装配HTTP/生产入口，没有创建watchLog/观看计数或真实OSS/FC验证。
