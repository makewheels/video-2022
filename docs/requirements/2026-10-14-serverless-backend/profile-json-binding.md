# S06.e2 资料更新 JSON 绑定基线

旧 Spring HTTP 与真实隔离 Mongo 实测；固定合成用户，未调用生产 API。

| JSON 请求 | 旧结果 | 持久化 |
| --- | --- | --- |
| `{"nickname":123,"bio":true,"ignored":{"nested":1}}` | HTTP200/code0 | nickname 为字符串 `123`，bio 为字符串 `true`，未知属性忽略，token 保留 |
| `{"nickname":[]}`、`{"bio":{}}` | HTTP200/code1/未知错误 | 昵称、简介与 updateTime 均不变 |
| 根数组、根字符串、`null`、空正文、畸形 `{` | HTTP200/code1/未知错误 | 昵称、简介与 updateTime 均不变 |

测试位于 `ProfileAvatarHttpContractTest`，与既有 UTF-16/缺失清空/头像流程共同跑8项。此阶段只新增2项测试，没有改变 Java 运行逻辑，也没有新增 Python JSON 解码器。

Python S06.e1 的字符串/None 端口不能代表完整旧 HTTP 绑定：不能简单拒绝所有非字符串字段，也不能把数组/对象转换成字符串后继续写入。浮点数、数值词法、重复 JSON 键、非法编码、其他 content-type 与完整请求管线仍待专门验证；表中结果不推广到未覆盖类型。

验收门槛：后续 JSON 适配须与旧 HTTP 响应及隔离持久化共同对照；在此之前不装配生产更新路由。回退仅回退本阶段测试/文档，无资源或业务数据回退。
