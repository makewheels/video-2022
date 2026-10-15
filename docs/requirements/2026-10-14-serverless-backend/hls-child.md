# HLS 子清单迁移

新增纯业务 ChildPlaylists 和只读 MongoHlsReads，按现有 WatchService.getM3u8Content
及 M3u8Util.getInitSegmentUri 保留旧行为：先验证视频、任务和档位，再鉴权；关联视频必须
同时匹配 linkVideoId 和 transcodeIds；每个签名分片必须属于源视频及任务。

TS、fMP4 init MAP 和媒体分片共用已有 HMAC 签名能力。签名使用当前观看视频身份，
分片归属使用源视频身份。URL 保留原始拼接和 Java null 字符串语义，不新增转码状态过滤。
保留注释、未知分片、CRLF 中未替换行的 CR、Java split 丢弃尾部空行、MAP 首项及属性解析怪异行为。

本阶段提供业务与持久化适配能力，尚未注册 HTTP 路由、主清单或 file/access。
未验证实际 Java HTTP 内容类型，也未宣称完整播放可用；生产入口和线上流量未改变。

验证涵盖跨视频、跨任务、档位错误、关联视频、私密授权、签名声明、空身份、MAP 属性、
换行和真实本地 Mongo 文档映射。Linux CI 结果以 PR 检查为准。
