# 阿里云官方依据与项目取舍

读取日期：2026-10-14。区分官方能力、项目判断和待实测项。官方支持某能力不等于项目已验证；实施每个子阶段前重读相关资料并记录限制变化。

| 官方资料 | 说明摘要 | 项目决策与待验证 |
|---|---|---|
| [冷启动优化最佳实践](https://help.aliyun.com/zh/functioncompute/best-practice-for-reducing-cold-start-latencies) | 启动包含代码准备、运行时和初始化；建议精简依赖、选择语言及资源 | 普通 API 不加载媒体依赖。Handler 优先候选但要做 FC 端到端测量；官方也提供常驻方案，本项目按用户要求不采用 |
| [事件函数](https://help.aliyun.com/zh/functioncompute/creating-an-event-function)、[Web 函数](https://help.aliyun.com/zh/functioncompute/creating-a-web-function) | 内置 handler 与 Web 应用入口不同，可使用代码包 | 外壳可替换、业务共享；FastAPI 不是 FC 内置能力；构建目标 Linux 依赖，不上传 Windows 环境 |
| [HTTP 调用协议](https://help.aliyun.com/zh/functioncompute/http-trigger-invoking-function) | HTTP 与 event/response 转换，涉及编码、header 和状态码 | Base64、302、m3u8、CORS/sendBeacon 在外壳适配；FC envelope 不进入业务。平台无法完整表达的旧参数语义必须单独验证 |
| [实例事件](https://help.aliyun.com/zh/functioncompute/instance-level-events)、[单实例并发](https://help.aliyun.com/zh/functioncompute/configure-the-concurrency-of-a-single-instance) | 实例按需创建销毁；生命周期说明区分内置单请求与自定义多并发 | 内存不作持久状态；比较入口的并发、连接池和扩容成本，不只比较 import；实际运行时限制要回读和压测 |
| [数据库集成](https://help.aliyun.com/zh/functioncompute/cloud-service-integration/) | 数据库共享跨实例状态，可配置 VPC | 保留现有 Mongo，测网络/首连/查询；VPC 不是跨云自动互通，不擅自迁库 |
| [异步任务最佳实践](https://help.aliyun.com/zh/functioncompute/best-practices-for-asynchronous-tasks) | 异步任务可观察和管理执行状态 | 平台执行状态与业务成功分开；业务 attempt/幂等/恢复用公共协议表示。任务 SDK 放适配层，不假定恰好一次 |
| [资源限制](https://help.aliyun.com/zh/functioncompute/limits-of-usage) | 时间、磁盘、正文、并发及代码包大小有限制 | 按任务核对实际配置，不将平台上限当已有 timeout；大文件不经过普通 API，不进入任务正文 |
| [CreateFunction](https://help.aliyun.com/zh/functioncompute/api-fc-2023-03-30-createfunction) | 代码包可经 OSS 发布，需要读取对象权限 | Git 提交在 CI 构建 ZIP，OSS 只分发产物；校验来源和摘要，角色最小授权 |
| [版本和别名](https://help.aliyun.com/zh/functioncompute/manage-aliases) | 别名指向版本，用于发布、灰度和回退 | 版本关联 Git SHA/摘要，受控切换且保留前版；别名不能替代在途任务/写所有权交接 |
| [CPU 计费变更](https://help.aliyun.com/zh/functioncompute/product-change-description-on-optimizing-function-billing-mode) | 有调用小时最低消费、延迟释放，缩到零停计费 | 不宣称无请求时总账单必为零；Mongo/OSS/消息/网络分项核算，不为每个 API 机械建函数 |

## 决策方式

1. 记录场景、相关官方约束、候选方式与来源；只重读当前阶段相关内容。
2. 优先官方支持且符合用户目标的简单方案；与无常驻等约束冲突的建议不照搬。
3. 取得真实性能、协议、费用和故障证据，注明函数配置与样本；不足时标为未确认。
4. SDK 类型/异常不进入公共业务，保持可迁移边界；缺失/冲突的文档通过 API 回读和原型验证。

## 当前证据边界

- 本机初始化数据支持优先调查 Handler，但没有证明 FC 的时延和成本。
- Git→CI→OSS→FC 来自用户要求；官方包/版本能力支持组合。不可变键、manifest 和 Git 来源验证是项目工程约束，不是声称 OSS 自带 Git 单向同步。
- 只实现阿里云和 Mongo；适配接口不代表未来换云/换 PostgreSQL 零成本。
- FC 异步任务不自动解决 Mongo 写库/投递间隙；可靠唤醒仍需证明。

## S01 再核对（2026-10-14）

重读上表 HTTP 调用、事件函数和单实例并发资料：FC 内置运行时的 event/response 协议及 Base64 属于运行外壳；解码路径使用 requestContext.http.path，不二次解码。保持原型实现，移动到 FC 适配器后回归；原入口路径不变。文档的多值 header/query 合并行为列入 S05 兼容调查，未声称字符串字典已覆盖所有生产场景。

并发配置支持不等于每种运行时/实际资源均支持相同吞吐。本轮只验证应用工厂、诊断序号和 Mongo 池在代码并发下复用，云端实测保留 S04。
