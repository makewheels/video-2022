# 清单 HTTP 接入

build_watch_application 现同时组装播放信息、主清单和子清单，三条路径共享调用方的
RequestCycle；不重复执行身份解析和请求日志。默认生产 FC/ASGI 入口保持原状。

主清单无需新增必填参数；子清单要求 transcodeId、resolution。播放身份及短期授权
只从查询参数读取，保留缺失 client/session 的 null 语义，不信任同名 header。
业务拒绝转换为既有错误信封，异常 detail 独立进入 exceptionLog，成功返回原始清单字符串。

对齐已实际跑通的 Java 完整 Boot HTTP 基线：默认及 text/plain 为
text/plain;charset=UTF-8，application/vnd.apple.mpegurl 添加 UTF-8，application/json
仍返回原始字符串且无 charset。质量权重与显式字符集保留基础处理，复杂 Accept 语法、
全部异常媒体类型及与完整 Spring 协商算法的逐项等价尚未验证。

本地新增 8 项 HTTP 测试，连原播放信息及架构共 22 passed、Ruff 通过。
通过真实隔离 Mongo 和 ASGI 请求串通私密视频的播放信息→主清单→子清单，
后两步不再携带用户 token；确认三个请求只落三条日志，无新增 client/session 写入。
同时覆盖四种已核对 MIME、缺参、跨视频拒绝、私密异常详情及缺失视频空清单。

本阶段结束于分片 URL 生成；file/access、真实 OSS 下载、计费写入、线上流量和
完整后端根路由的生产装配仍不在本次验收范围。Linux CI 结果独立确认。
