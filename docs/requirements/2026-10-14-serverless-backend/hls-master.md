# HLS 主清单迁移

MasterPlaylists 保留 WatchService.getMultivariantPlaylist 的读取和输出顺序。
视频不存在返回空字符串；私密视频先鉴权；只发布 provider 对应成功状态、归属正确且
tsFileIds 非空的任务。currentProvider 非 null 时覆盖 provider，包括未知 provider。
任务按 Mongo 查询返回顺序输出，不新增排序或逐分片存在性检查。

保留 BANDWIDTH / AVERAGE-BANDWIDTH 的 null 字符串；实际尺寸优先且逐维回退；
CODECS、FRAME-RATE、VIDEO-RANGE 按现有非空条件输出。帧率用三位 HALF_UP，
已用本机 JDK 21 核对 60000/1001、整数、正负半舍入、负零、尾部斜杠六组结果。
关联视频的播放签名仍使用被观看视频，音轨读取使用源视频。

只有 MPS 且 fallbackCount=1、源视频恰好一条音轨、合法语言和正声道数时输出无 URI
的 muxed audio 声明。读取已有 mediaInfo.audioTracks.tracks，不新增字段或数据库写入。
现有正常 BSON 类型已覆盖；Fastjson 对异常历史标量的全部强制转换语义尚未逐项核对。

本次本地 Ruff 和播放相关 64 项测试通过，包含真实 Mongo 字段读取及主/子清单测试。
本地完整套件实际 441 passed、2 deselected（Windows 无符号链接权限）、1 条已有警告。
随后在临时目录编译当前 WatchController 源码，以真实 Spring MockMvc standalone 和替身
WatchService 实测内容协商：Accept */* 或 text/plain 返回 text/plain;charset=ISO-8859-1，
application/vnd.apple.mpegurl 返回同 MIME 加 ISO-8859-1，application/json 返回 application/json。
这只证明 standalone 转换器行为，尚未证明完整 Boot 配置的 charset、过滤器及日志合约。
Linux CI 另行确认。仍未注册 HTTP 路由，未确认完整 Java HTTP 合约，
file/access 重定向、计费及可靠异步写入未完成，不作为完整播放验收或生产切流依据。
