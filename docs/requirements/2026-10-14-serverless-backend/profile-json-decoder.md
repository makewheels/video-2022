# S06.e3 未装配资料 JSON 解码能力

`decode_profile(bytes)`只输出普通ProfileInput（nickname/bio），不导入ASGI/FC/数据库，不注册路由。接续S06.e1的UTF-16业务校验和Mongo更新，但不取代鉴权/同步日志/正式HTTP参数绑定。

## 旧 HTTP 实测与对应实现

- 缺失/null字段成为None；未知属性忽略。字符串保持原值，整数/布尔转字符串。
- JSON小数/指数保留原始词法：1e2、1E+2、-0.50、-0不经过Python浮点数或整数再格式化。JSON数字解析hook只保留字面量。
- 重复属性按后值，但每次出现都执行DTO标量类型检查：前值是31字符字符串、后值last时最终接受last；前值数组、后值valid仍绑定失败。使用有序object-pairs标记避免Python普通dict提前吞掉前值。
- 根值必须对象，已知字段数组/对象、缺失/畸形正文均固定ProfileBindingError；异常不带正文或parser摘录。
- 旧真实HTTP拒绝第二个根对象或尾随垃圾，资料不写入；解码器仅允许根对象后的JSON空白。该结论来自实测，不能套用其他Jackson版本/默认配置推断。

## 验收边界

仅针对本阶段验证的UTF-8正文；BOM/UTF-16/UTF-32、非法/孤立代理项、超深嵌套/极端数字长度、其他content-type等仍须旧HTTP与Mongo对照。未实现完整编码兼容或正式路由表，不能把当前UTF-8解析失败自动变成生产新规则。

成功解码后还必须调用ProfileUpdates做旧长度/先后顺序校验，不能从解码结果直接绕过业务写库。不存在生产更新或双写；所有Mongo写只在随机隔离库。回退删除本阶段模块/测试/文档，无资源回退。
