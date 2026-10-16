# S09.m.1 统一隔离观看能力装配

状态：本地验收通过，必要CI另查。现役`bootstrap`/FC/web原型入口保持不变；显式`build_backend_application`现在装配观看能力组，并由同一ASGI工厂驱动。

`ViewingApplication`只选择路由，不再启动第二层请求生命周期。历史/进度、旧心跳、播放会话和可选观看日志共享`users.cycle`及同一个数据库provider；心跳复用进度适配器，历史封面复用调用者提供的签名能力。私密播放授权/HLS沿原WatchApplication路由，不绕过权限。

观看日志必须显式传入`ip_locations`能力；未提供则该路由404且不访问Mongo，不从环境推测收费API凭据。测试传入真实Mongo缓存+合成IP供应商，两次IP lookup只触发一次合成供应商调用。不新建生产缓存/索引、不联网调用供应商。

实际Python完整757passed/2deselected（Windows符号链接权限）/0skipped/1既有warning；新装配/原播放路由/架构focused17passed，Ruff/500行/空白通过。仅Python装配变化，不为此重复Java全套，既有Java契约证据与本次机器验证分开。

- 同一ASGI后端串通私密watchInfo→主/子HLS→观看日志→会话start/heartbeat→旧心跳→进度/历史→text/plain退出→历史清理；10个应记requestLog的请求恰好10条，旧心跳零requestLog，零异常。
- 实际Mongo观看次数/日志/缓存/会话用户/进度与历史一致；未提供IP能力和未知路由不连接数据库。
- 同一根应用直接并行dispatch12个混合匿名/登录请求：会话ID不同、用户身份与client准确、12条日志且零异常。不把线程内dispatch测试称为真实FC网络并发。

本批为m.1装配，不宣称m全部完成。m.2的真实旧CORS/OPTIONS/HEAD、扩展编码与网络矩阵继续固定/验证；真实FC及各端验收仍为n，依赖S03/S04。回退本批提交恢复原隔离工厂路由集合，未影响生产入口/数据。
