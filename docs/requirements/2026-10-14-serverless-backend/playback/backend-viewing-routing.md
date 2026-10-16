# S09.m.1 统一隔离观看能力装配

状态：本地验收通过，必要CI另查。现役`bootstrap`/FC/web原型入口保持不变；显式`build_backend_application`现在装配观看能力组，并由同一ASGI工厂驱动。

`ViewingApplication`只选择路由，不再启动第二层请求生命周期。历史/进度、旧心跳、播放会话和可选观看日志共享`users.cycle`及同一个数据库provider；心跳复用进度适配器，历史封面复用调用者提供的签名能力。私密播放授权/HLS沿原WatchApplication路由，不绕过权限。

观看日志必须显式传入`ip_locations`能力；未提供则该路由404且不访问Mongo，不从环境推测收费API凭据。测试传入真实Mongo缓存+合成IP供应商，两次IP lookup只触发一次合成供应商调用。不新建生产缓存/索引、不联网调用供应商。

实际Python完整757passed/2deselected（Windows符号链接权限）/0skipped/1既有warning；新装配/原播放路由/架构focused17passed，Ruff/500行/空白通过。仅Python装配变化，不为此重复Java全套，既有Java契约证据与本次机器验证分开。

- 同一ASGI后端串通私密watchInfo→主/子HLS→观看日志→会话start/heartbeat→旧心跳→进度/历史→text/plain退出→历史清理；10个应记requestLog的请求恰好10条，旧心跳零requestLog，零异常。
- 实际Mongo观看次数/日志/缓存/会话用户/进度与历史一致；未提供IP能力和未知路由不连接数据库。
- 同一根应用直接并行dispatch12个混合匿名/登录请求：会话ID不同、用户身份与client准确、12条日志且零异常。不把线程内dispatch测试称为真实FC网络并发。

本批为m.1装配，不宣称m全部完成。m.2的真实旧CORS/OPTIONS/HEAD、扩展编码与网络矩阵继续固定/验证；真实FC及各端验收仍为n，依赖S03/S04。回退本批提交恢复原隔离工厂路由集合，未影响生产入口/数据。

## S09.m.2 旧OPTIONS/CORS/HEAD

真实旧MVC/隔离Mongo固定16组OPTIONS/预检：GET客户端ID、POST播放会话、ANY旧心跳及受保护资料。普通OPTIONS的Allow/Accept-Patch与带Origin预检不同；未许可预检旧行为是空200/无许可头，并不是通用403。唯一客户端ID GET保留`@CrossOrigin`的`*`、GET、请求header回显和1800秒缓存，无credentials；其他业务不加全局CORS。受保护OPTIONS仍401、零requestLog，响应仍带FrameworkServlet默认Allow。

新RoutingApplication只在已装配路由处理OPTIONS，复用同一cycle，不创建client/session或调用业务。未配置的头像/播放/IP路由不凭OPTIONS暴露；有效GET/HEAD客户端ID响应按旧规则补CORS，保留同源无跨域头。各GET能力允许HEAD但保留原HEAD metadata，ASGI传输不输出正文并保留GET正文长度。

本机实际完整Python777passed/2deselected（Windows符号链接权限）/0skipped/1既有warning；新增预检/跨域/HEAD与旧装配/架构focused31passed；Java2passed/0skipped（16组矩阵和HEAD/GET对照，含实际client GET跨域）、Checkstyle成功，Ruff/500行/空白通过。首轮Python探测用Database.client误指MongoClient属性，已改显式集合访问后复验，没有放宽业务断言。

以上为真实MVC/ASGI和Mongo，非真实FC/浏览器代理验收；恶意/畸形Origin、任意多值头、TRACE及所有编码边界尚未逐项验证。m.3继续正文编码兼容，保持S09.m部分状态；不把m.2等同所有入口验收完成。

## S09.m.3 正文字节编码

真实旧MVC/隔离Mongo固定92组字节级样本，原始正文以Base64保留，覆盖资料更新/旧心跳/会话start/exit四条路径；UTF-8、UTF-16和UTF-32的大小端、有无BOM、声明/缺省charset、ISO-8859-1及损坏UTF-8。Java默认执行再次核对样本，不仅导出。

共用`body_text`按实测保留两种路径：JSON流对Unicode使用字节/BOM检测，非Unicode声明走Reader；exit先String转换，不自动识别UTF16/32，缺省UTF8或显式charset并保留坏字节替换。前者遇损坏UTF8拒绝写入，后者原样保留旧替换字符写入，不能统一“更严格”校验。数值词法/重复字段及业务逻辑不变。

实际完整Python869passed/2deselected（Windows符号链接权限）/0skipped/1既有warning，focused118passed；Java3passed/0skipped（92组字节+既有50组心跳/108组会话矩阵）/Checkstyle成功，Ruff/架构/500行/空白通过。原profile/heartbeat JSON、会话快照和装配矩阵均重新实际通过。

这完成m.1–m.3的主要隔离装配/入口/编码矩阵；必要CI仍按精确head核对。任意字符集/畸形代理、异常旧BSON、真实FC envelope/网络和全端播放不由这些样本代替，继续归入S09.n及各域剩余验收。没有生产装配或配置/数据变更，回退本批提交恢复原解码。
