# S08.a 播放授权与旧文件签名

实现普通AccessClaim端口、HMAC适配和PlaybackAuthorization用例；不导入HTTP全局、
线程上下文或云SDK。保留七字段原顺序、换行拼接、null转空串、UTF8/HMAC-SHA256
及小写hex；固定5分钟双向含边界窗口、Java Long解析/溢出/Math.abs语义，不新增
凭据字段/过期规则。私密视频允许owner或video/client/session绑定的短期playlist签名，
public/unlisted仍按原授权规则。签名URL沿用playbackTime/playbackNonce/playbackSign。

## 独立证据

用JDK21在系统任务临时目录直接编译当前Git的FileAccessSignatureService.java
（只提供所需Spring注解JAR，无修改方法体），注入固定合成secret，实际得到普通、
null/中文、孤立UTF16代理项三组签名。Python必须匹配这些Java黄金值，UTF8编码
包括Java对孤立代理项的问号替换；不以Python自签自验代替跨语言证据。

2026-10-15新增22项及架构8项共30passed，Ruff通过。覆盖黄金值、七字段逐项
替换失败、窗口±300000包含/±300001拒绝、Long词法、空签名短路、owner/匿名/
跨视频/跨client/过期播放授权。全为固定合成值，不读生产签名密钥。

## 未完成与回退

本阶段尚未接入getWatchInfo/m3u8/file-access HTTP；nonce仍为注入，不能称播放
链路已迁移。接续需Mongo视频/进度与Context映射、旧HTTP黄金、主/子播放清单、
TS/fMP4/init/audio和302/访问计费任务；云端冷启动/首帧/权限仍待实测。
回退新增三模块和测试，无线上资源、数据或配置变化。
