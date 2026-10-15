# S06.f1 资料读取隔离 ASGI 组合验证

测试专用 Starlette 应用把 from_asgi_metadata、IdentityResolver/MongoUserReads、Profiles/MongoAvatarFiles、RequestCycle/MongoRequestJournal 与普通响应值串起来，最后由 Starlette Response 输出。两个测试路由为getMyProfile（受保护）与getChannel（公开），不修改现有web:app/FC/Application，不提供可部署的新入口。

## 实际验证

- 5份既有真实Java黄金响应：profile/channel各signed/fallback、channel-missing，逐项对照status/contentType/body；有序重复token header首值不被后值覆盖，公开频道允许无有效身份。
- user/file实际读自随机回环隔离Mongo；比较调用前后原文档不变。签名端口仍替代，检查key与86400秒，不调用OSS/STS。
- 日志实际落同一随机隔离库，检查一条日志、原URI/不含query的URL/raw query/peer/首个token值/实际HTTP状态。
- 3份拒绝场景与旧401黄金响应完整对照：缺身份、错误header覆盖有效query、空header回退到重复query首个错误值；均不落日志、不签名。
- 含点header日志失败仍保留资料响应；随后匿名请求401不落日志，再正常身份请求成功只落一条，未携带前次身份。

这是TestClient驱动的进程内ASGI HTTP测试，不是TCP/代理/真实FC请求。固定合成身份与URL，无生产凭据或生产用户数据。测试只安装两个GET路由；缺必需参数、其他方法/404、完整域异常、JSON绑定/更新、登录和头像上传不在此组合覆盖范围，不能把测试中的断言当生产参数绑定实现。

完整Python264passed/0skipped/1既有warning，Ruff/500行/空白通过。没有改Java或黄金文件，因此不重复Java套件。环境/缓存/dbpath等均系统任务临时目录并清理，自建mongod终止、随机库删除。

S06、具体签名适配、完整正式HTTP路由与FC网络验收仍未完成。回退删除本阶段测试/文档，不影响线上资源和数据。
