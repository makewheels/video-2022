# S15.b.1 订阅状态与列表读取

状态：隔离实现与本机验收完成（2026-10-29）；基线 `162c6d37`。本批仅迁移 `/subscription/getStatus` 与 `/subscription/getMySubscriptions` 的普通能力、现有 Mongo 读取和显式隔离 HTTP 装配，不包含 subscribe/unsubscribe、计数写入、通知或生产切流。当前 head 的完整 CI 结果持久记录于本批 PR。

旧依据为 SubscriptionController/Service/Repository、Subscription 和 CheckToken 拦截器。两个读取接口均要求登录，登录先于绑定；状态按 userId/channelUserId 原字符串精确匹配，不检查频道存在。列表按 createTime 降序、默认 skip=0/limit=20、正 limit 最大50，直接返回原 channelUserId，不去重、过滤失效频道或补查用户。真实 Java 实测：负 skip 等同0；0或负 limit 不限条数，不能按 PyMongo 的负数硬限制处理。空值、BSON类型和重复参数也按黄金样本保留，不自行增加校验。

先运行真实 Spring/Mongo 生成固定合成样本及完整前后 BSON，再实现 Python 对照。验证包括身份、排序/分页/类型/默认值、HEAD/OPTIONS/不支持方法、同步请求/异常日志；复用同一 RequestCycle，默认工厂不安装读取或写入入口。仅使用系统临时目录内独立 loopback Mongo，云端和实机验收不由本机测试替代。

实际 Java 2项/0skipped（26组 HTTP/完整 BSON 样本，生成后再次固定文件重放）；Python 新增29项，与赞踩、统一播放/观看装配和架构共108passed/0skipped/1既有warning。Ruff、Checkstyle、500行和空白门禁通过。业务 user/subscription BSON 每次请求后完全不变，身份/同步日志继续写既有日志集合。真实 FC、全端实机及云网络未跑。

回退本阶段 PR 的代码即可，无业务数据迁移或配置回退；禁止恢复旧数据库备份覆盖业务数据。
