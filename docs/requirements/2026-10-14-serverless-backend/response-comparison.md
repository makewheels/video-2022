# S05.b 离线响应对照工具

范围：读取两个受控 JSON 测试响应，不发网络请求，不采集生产用户数据，不写输出文件。不是 Python 业务接口实现或线上兼容通过证明。

输入结构固定为 `{status: 整数, contentType: 字符串, body: JSON值}`，由测试夹具分别保存旧/新实现的 HTTP 状态、Content-Type 和解析正文。不能以只存 body 的样本替代 HTTP 状态验收。

比较 key 集、严格 JSON 类型（bool 不当成整数）、值、null、数组长度与顺序。只允许 `body.data.list[*].coverUrl` 的有效 HTTP(S) URL 中 `Expires`/`Signature`/`x-oss-date`/`x-oss-expires`/`x-oss-signature` 值变化；保留这些参数是否存在、重复次数、URL host/path、其他 query 与 fragment。URL 无签名、畸形或缺字段不被归一化放过。不忽略 OSSAccessKeyId、安全 token 或任意其他 URL。归一化不证明签名有效、TTL 正确或播放权限，留业务实际验收。

报告仅列受控字段路径及差异类别，不输出 body 值、签名、用户 ID、昵称、手机号或输入文件路径；未知字段名也不回显。错误输入只返回固定错误消息。脱敏是输出边界，并非把所有数据值替换成相同占位符后假称一致。

用法：`python serverless/contracts/compare_responses.py old.json new.json`。两个输入必须在系统任务临时目录，以受控夹具产生并清理，不提交原始用户响应。exit 0 表示有限规则下匹配，1 表示差异，2 表示输入错误。无需第三方依赖；测试复用 serverless/tests，CI 原型 job 自动发现。

验收覆盖签名动态字段、签名存在性、路径/身份变化、业务字符串、空值/缺字段、顺序、类型与整数/浮点差异、HTTP/错误码变化、未知字段名及输入错误不泄密。后续 Java 受控 HTTP 黄金夹具单独阶段补齐，不重放带日志副作用的生产链路。

回退仅删除离线工具及其测试；无部署、数据或业务接口影响。
