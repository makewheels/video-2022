# S15.a 旧视频赞踩契约

由 `VideoLikeHttpContractTest` 在真实 Spring MVC 与独立本机 Mongo 上生成 56 组完整响应/操作前后 BSON。只含合成身份和数据，不访问 OSS、转码或线上数据库。

在 `server` 下运行该测试，传入 `-Dcontract.video-reactions.output.dir=<系统临时目录>` 可重新生成；不传时直接与本目录 `cases.json` 比较。测试 Mongo 必须显式指向隔离实例。Python `test_video_reactions.py` 使用相同样本做实际 Mongo/ASGI 对照。

仅归一化新生成的 reaction ObjectId 和服务端日期；既有 ObjectId 用 `{"$oid":"..."}` 保留与 BSON String 的区别，固定日期保留毫秒值。样本覆盖 toggle、公开状态、权限/绑定顺序、重复参数、ID 转换及独立计数写入的失败状态，不代表真实 FC、生产数据所有 BSON 类型或实机端到端已验。
