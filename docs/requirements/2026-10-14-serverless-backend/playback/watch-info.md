# S08.b 播放信息HTTP能力

新增WatchInfos、MongoWatchReads、WatchApplication及显式装配工厂，对应原
GET /watchController/getWatchInfo。保持Controller先检查watchId、Service再查询；
不存在时code22及原自定义异常日志详情，私密非owner直接code1且不调用封面/进度。
保留五个WatchInfoVO字段，不按READY额外拦截。

RequestUtil.getContext只取query首值，因此client/session头不替代query；videoId
由watch记录覆盖。缺少/空白身份生成临时NanoId，不创建client/session记录。封面
存在key时签2小时，进度登录优先viewerId、否则clientId；无记录0、已有记录null
仍null。播放URL保留internal-base-url及原字段/HMAC，不从请求Host推导。

本次2026-10-15真实回环随机Mongo+ASGI HTTP新增6项，连用户HTTP/架构focused
24passed、Ruff通过、1既有warning。覆盖签名、私密owner/拒绝、进度、临时身份、
缺封面和watch异常详情；当前Java源码与既有WatchServiceTest用于核对，不把它们
说成本次完整Java HTTP黄金重跑。自定义异常仅增加内部detail，旧登录异常仍无detail，
同次用户HTTP回归通过。

新工厂显式接收原秘密、内部域名和共享cycle；不自动替换稳定生产入口，也尚未注册
主/子播放清单、分片302/计费。此阶段返回播放URL不代表这些URL已由Python处理，
不能声称可完整播放。下一步HLS子清单、主清单和分片交付；回退本阶段模块/异常
detail支持，无线上配置、数据或资源变更。
