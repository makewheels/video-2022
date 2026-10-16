# S10.b.2 上传状态轮询

显式隔离创建装配同时提供`GET /video/getVideoStatus?videoId=...`，与创建/观看等共用既有身份和日志周期。不开启创建能力时不安装此路由，生产入口未切换。

保持CheckToken先于绑定，未登录401不记请求/异常日志；Controller和CheckService依次检查视频存在、用户存在、uploader匹配，再读取业务响应。权限用uploaderId，ownerId不替代上传者。视频不存在22、用户不存在14、上传者不匹配23；null uploader沿旧通用错误处理，不新增业务码。

只返回videoId/status/isReady，精确READY才就绪；TRANSCODING_PARTLY_COMPLETE不是READY。Mongo字段缺失沿旧构造默认CREATED，显式null则返回null；未知状态按原值返回，不新增枚举校验。兼容旧ObjectId，没有业务写入、文件读取、任务提交或ID分配。

`VideoStatusHttpContractTest`以真实旧MVC/Mongo固定19组HTTP响应、日志数和只读副作用；覆盖全部已定义状态、大小写/未知、缺失/null、uploader权限、ObjectId和鉴权/绑定错误。另直接核对HEAD/OPTIONS/不支持方法与原方法日志。Python同样本经真实ASGI/独享Mongo逐项比较，并验证授权期间视频/用户消失时不返回过期或未授权状态。

这完成S10.b的创建与旧轻量状态子项，不等于上传/MD5/转码已结束。原片完成可靠受理和AUTO去重仍待S12及S10后续；完整Web/Android/CLI上传及真实FC未验。回退本批提交即可撤销隔离状态路由，既有创建和文件完成仍按此前批次运行。

2026-10-16接续实测修正：旧MVC对不支持方法在构造拦截器链前返回通用错误，因此即使没有token也返回200/code1；只记异常、不记请求日志。Python状态入口在身份解析前处理该分支，旧Java及Python同时断言OPTIONS/HEAD两条请求日志和有/无token的POST两条异常，避免把未验证假设写成契约事实。

本机接续验收：Python 3.12.13完整1084passed/0skipped/1既有Starlette弃用warning，Ruff通过；JDK21真实独享回环Mongo旧Java状态HTTP契约2passed/0skipped，Maven validate通过。初次Python未设置只读探针数据库名导致1skip，补齐隔离配置后完整复跑无skip；首次旧Java方法日志断言失败已按上述实际行为修正并复验。运行环境、Java构建和Mongo数据均在系统临时目录，结束后关闭独享进程并清理；没有生产云调用。
