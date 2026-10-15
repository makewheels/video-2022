# 文件重定向纯用例与Mongo读取

目标：实现已通过旧Spring管线固定的时间→HMAC→tsFile key→3小时预签名，保留通用code35与过期详情、缺文件code31。只读端口与签名复用，ObjectId查询不回退String，不额外按owner/type/deleted筛选；空key不按缺失处理。

不启动后台线程、不写访问日志/费用、不安装HTTP或生产入口。旧异步日志缺口已由独立#195证明；可靠计费及单所有者为S12门槛，纯重定向用例不能宣称完整file/access迁移。

验收：固定合成HMAC及可观察端口验证错误顺序/不访问存储/三小时TTL，真实回环随机Mongo验证ObjectId/String、缺失/null/空/数字key与原文档不变；完整Python回归。无真实OSS/云收费调用。回退只撤销新库文件。

状态：CPython3.12.13/真实回环随机Mongo完整486passed、0skipped、1既有Starlette warning，Ruff通过。新增21项，含真实ObjectId/缺失字段、不可篡改签名上下文、无重试签名失败。首次Ruff长行失败换行后通过；未修改Java或重复其契约。
