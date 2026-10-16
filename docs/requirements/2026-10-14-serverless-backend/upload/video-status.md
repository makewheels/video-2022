# S10.b.2 上传状态轮询

显式隔离创建装配同时提供`GET /video/getVideoStatus?videoId=...`，与创建/观看等共用既有身份和日志周期。不开启创建能力时不安装此路由，生产入口未切换。

保持CheckToken先于绑定，未登录401不记请求/异常日志；Controller和CheckService依次检查视频存在、用户存在、uploader匹配，再读取业务响应。权限用uploaderId，ownerId不替代上传者。视频不存在22、用户不存在14、上传者不匹配23；null uploader沿旧通用错误处理，不新增业务码。

只返回videoId/status/isReady，精确READY才就绪；TRANSCODING_PARTLY_COMPLETE不是READY。Mongo字段缺失沿旧构造默认CREATED，显式null则返回null；未知状态按原值返回，不新增枚举校验。兼容旧ObjectId，没有业务写入、文件读取、任务提交或ID分配。

`VideoStatusHttpContractTest`以真实旧MVC/Mongo固定19组HTTP响应、日志数和只读副作用；覆盖全部已定义状态、大小写/未知、缺失/null、uploader权限、ObjectId和鉴权/绑定错误。另直接核对HEAD/OPTIONS/不支持方法与原方法日志。Python同样本经真实ASGI/独享Mongo逐项比较，并验证授权期间视频/用户消失时不返回过期或未授权状态。

这完成S10.b的创建与旧轻量状态子项，不等于上传/MD5/转码已结束。原片完成可靠受理和AUTO去重仍待S12及S10后续；完整Web/Android/CLI上传及真实FC未验。回退本批提交即可撤销隔离状态路由，既有创建和文件完成仍按此前批次运行。
