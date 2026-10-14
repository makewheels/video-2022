# S06.f2 更新资料隔离 ASGI 组合

仅在测试内安装POST /user/updateProfile；异步读取正文后，在线程池组合RequestCycle鉴权/日志、decode_profile、ProfileUpdates和MongoProfileWrites。正式Application/web/FC入口与路由不变。

## 实测15项

- 4种成功正文（普通资料/空对象/数字指数与布尔/重复字段及未知属性）完整对照旧success黄金响应；实际Mongo只改变nickname/bio/updateTime，其他字段保留，更新端口恰好调用一次。
- 昵称UTF-16超长且简介同时超长仍优先昵称错误，单独简介超长保持旧错误；两份Java黄金响应完整匹配，更新端口调用0次，日志落1条。
- 6种JSON绑定失败（畸形/root null/容器/前一重复容器/第二根/尾随垃圾）匹配旧通用绑定错误黄金，业务0次写，日志保存最终HTTP200。
- 缺身份与错误token面对畸形JSON均旧401，鉴权先于绑定，业务/日志均0次写。
- 含点header导致日志失败，旧成功响应和已完成资料写保留，更新端口实际计数1次；后续无身份请求401，不落日志、不携带旧身份。计数包装器仅观察调用并委托真实Mongo适配器。

完整Python297passed/0skipped/1既有warning，Ruff/500行/空白通过。未改Java/黄金，因此不重复Java套件。临时env/cache/dbpath/log/pytest均系统任务临时目录，finally终止自建mongod和清理，随机库finally删除。

这是进程内ASGI/TestClient，不是TCP/代理/FC实测；只验证已验UTF-8 JSON路径，不证明其他编码/content-type、取消/网络不确定写入、完整异常/正式路由、登录/头像上传等通过。正文读取时机/限制还须生产协议适配专门核验。没有生产用户/双写或生产日志访问。

回退删除本阶段测试/文档，无线上资源/数据回退。S06与FC发布网络仍未完成。
