# S03.c 发布错误的操作定位

首次云发布失败缺少API上下文，不能只凭AccessDenied推断被拒的是哪一步。
FC SDK调用/执行、OSS读取/上传现在保留固定操作名及结构化错误码；不包含请求
参数、对象URL、环境值或SDK原始消息。GetBucketVersioning既有上下文保持。

2026-10-15实际deployment依赖组验证30passed，Ruff通过；覆盖GetObject/PutObject
拒绝与FC错误脱敏。该阶段没有重试已知无权限身份、创建RAM角色或扩大权限，
不解决实际发布阻塞，也不把诊断改进说成云部署通过。回退此提交即可，无云资源变化。

同日只读核对#183的CI38671383841后端job116215066338：LoginHttpContractTest
6项、ProductionLoginHttpContractTest3项、PublicVideoHttpContractTest5项均实际
0失败/0错误/0跳过，后端总计681项通过。本机离线Maven插件阻塞仍保留为未执行，
两者不混为本机测试结果。
