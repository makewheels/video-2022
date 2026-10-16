# 旧评论写入HTTP/Mongo契约

真实`CommentWriteHttpContractTest`生成43组合成输入/完整前后video、comment、comment_like/HTTP响应/日志。仅测试随机库，按旧注解建立实际comment_user_idx唯一索引；没有替代Mongo或mock ID分配。

实际新生成的ObjectId确认类型后替换为`<generated-comment>`或`<generated-comment_like>`，仅新建服务端Date及响应UTC时间归一为`<server-date>`；原有固定Date保留毫秒值。业务字段、计数和null均完整保留。Python同真实ASGI/Mongo比较并另测实际ID全流程/索引冲突/失败残留。

生成参数`contract.comment-write.output.dir`使用系统临时目录，成功后复制合成JSON交付。HEAD/OPTIONS/不支持方法由独立真实旧MVC测试固定；无生产/云调用，不代替客户端或真实FC验收。
