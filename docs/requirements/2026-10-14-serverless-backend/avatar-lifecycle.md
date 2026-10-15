# S06.k 头像生命周期与轻量OSS/STS适配

复用UserApplication新增三条旧头像路径，显式提供avatar_objects时才装配。保持
旧类型→owner→云调用顺序；创建File保留构造默认值，完成只更新user.avatarFileId
和updateTime，不更新FileStatus/metadata、不新增大小/MIME/MD5规则、不清旧avatarUrl。
匿名创建avatar/null文件及匿名凭证请求的旧缺陷仅在隔离库重现；不擅加鉴权或别名。

新增OssAvatars标准库HTTPS适配：24小时GET签名、HEAD存在性、AssumeRole RPC及
旧3小时凭证字段。调用有3秒超时，无后台线程/轮询/自动重试；403对象查询不能当404。
STS客户/网络错误返回null，沿用业务34；未知响应格式固定内部诊断，不返回云正文。
适配器不读取环境或真实秘密，role/bucket/endpoint/身份由后续受控装配注入。
可将同一OssAvatars实例作为头像签名与对象能力传给build_user_application，不增加
普通API包的云SDK依赖。线上STS权限、role时长/边界及真实对象尚未验。

## 独立证据

JDK21+项目锁定OSS Java SDK3.16.3、合成身份和固定2040-01-01过期，实际生成
普通key及中文/空格/加号/问号/百分号key的签名URL，作为Python黄金值。
另以项目aliyun-java-sdk-core4.6.2的RpcSignatureComposer实际生成固定STS请求的
HMAC值Ya5XgS46AmNFymqyIJIvqgRRhR4=；Python请求必须逐字匹配。均未发云请求。
第一次临时Java探针因混入多个SDK版本发生链接错误，改为精确依赖后取得上述结果，
不把失败运行当依据。临时Java源码和产物已finally清理。

[OSS官方V1 URL签名](https://www.alibabacloud.com/help/en/oss/developer-reference/ddd-signatures-to-urls)
与[STS AssumeRole参数](https://www.alibabacloud.com/help/zh/ram/developer-reference/api-sts-2015-04-01-assumerole)
支持请求格式；本阶段为兼容旧SDK，不擅自改为V4或更换业务凭证格式。V1在目标资源
是否仍可用，须云端验收，不能从离线签名匹配推断。

## 验证与回退

已实际隔离Mongo/HTTP验证创建→凭证→完成→动态头像读取、类型/owner先检查、
不存在对象31、凭证失败34、匿名旧边界；OSS适配测试使用合成传输，未上传文件或
取得真实STS token。没有生产入口/配置/线上数据变更，删除本阶段模块和装配即可回退。

2026-10-15实际Python3.12.12/临时Mongo8.3.7：focused25passed（含9项头像HTTP、
7项OSS适配、真实TCP及架构），再加入STS Java黄金断言后完整348passed、
2deselected（既有Windows符号链接权限）、1warning；Ruff通过。
