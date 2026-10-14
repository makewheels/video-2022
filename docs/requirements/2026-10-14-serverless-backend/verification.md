# 本次验证与未确认项目

日期：2026-10-14。状态：用户已确认开始，P1 隔离原型开发和本地验证；尚未部署 FC 原型、修改生产配置或切换业务流量。

## P1 本地原型进展

- 新增 `serverless/`，共用同一应用的内置 Python Handler 和 FastAPI Web 入口。仅健康检查及受鉴权保护的空请求/Mongo 单条只读诊断，不包含生产业务接口替代实现。
- Windows Python 3.12.12 实际运行 16 项测试通过，1 项真实 Mongo 查询因无配置 skipped；Ruff、500 行门禁及锁定依赖解析通过。测试覆盖请求 Base64/大小写头、文本 m3u8/302/binary 响应、诊断鉴权和错误脱敏、Mongo 连接池复用及真实本地 HTTP 统计。测试夹具不等于生产 Mongo 查询已通。
- S01 分层前的原型，两个入口各 20 个新解释器样本：Handler 模块加载 P50 30.793ms / P95 32.212ms，FastAPI P50 642.513ms / P95 781.559ms。**仅本机模块初始化，不含 FC 调度、网络、Mongo 认证或 API 业务，不能当成 FC 响应指标。** 因此优先验证内置 Handler，保留相同应用用于后续云端比较。
- 通过代理实际读取现有 `/video/getPublicVideoList?skip=0&limit=1`；只保留字段名称/类型/空值形状到 `serverless/contracts/`。它不是完整业务契约，不保存用户内容、ID 或带签名 URL。
- 本机 Docker daemon 不可用，未启动/修改 Docker；[原型 CI 38510090583](https://github.com/makewheels/video-2022/actions/runs/38510090583) 已实际 success，其中 Linux + 隔离 Mongo 17 passed；API/浏览器 E2E skipped，不等于生产网络或完整迁移验证。
- Infisical CLI 本机 session 过期，自动登录失败；先前已请求恢复。它不证明 Actions OIDC 失效，后续发布优先核验并复用 CI 取密身份，不重复索取密码或因本机单点停止独立工作。FC/生产 Mongo 仍未验证。
- 本地 FastAPI TestClient 对 httpx 给出弃用提醒；测试通过，尚未将其当作云端运行时结论。

## S01 原型分层验收（2026-10-14）

用户给定本轮截止为 **2026-10-14 17:00（Asia/Shanghai）**；目标只完成现有原型分层、验证和单独 PR，不扩到 S02/S03 或生产接口迁移。

- 实现 `business/ports/application/http/adapters/bootstrap` 边界；保留 `fc_handler.handler`、`web:app` 两个入口。配置读取集中装配层，业务无环境变量/SDK 依赖；FC envelope 从通用 HTTP 值移出。
- 同步用例被 FC 直接调用、ASGI 在线程池调用；进程应用和惰性 Mongo 池均通过锁防止首次并发重复创建。未加预热/保活，不改池大小和超时。
- 本地 Windows Python 3.12.12、锁定原依赖：Ruff 最终 0 错误；wheel 构建并重装到隔离环境通过，用 `-I` 确认从安装目录加载两个稳定入口；native 健康检查未加载 PyMongo/FastAPI。
- **实际在安装后的 wheel 上跑完整测试：43 passed，真实 Mongo 集成 1 skipped，已有 TestClient 弃用提醒 1 条**。临时环境、缓存和 wheel 均放系统任务临时目录并清理。没有连接生产 Mongo，不能把 skipped 算作通过。
- 验证覆盖：普通 Python 替代探测适配器/时钟；依赖规则及禁止依赖的反例（含相对导入）；健康/空请求无驱动；两个外壳的鉴权顺序、成功/403/404/405/503、错误脱敏、POST 正文/查询、m3u8/302/二进制；32 个并发调用的唯一序号、应用只创建一次和连接池复用。
- 上述并发是代码级并发检查，不代表 FC 并发配置、真实吞吐或冷启动已验。真实 Mongo 由 CI 隔离 Mongo 检查；PR 最终结果在对应 PR 描述追加，以 `feat-serverless-portable-layers` 查询，API/浏览器 E2E 跳过必须单列。
- 官方 HTTP 文档说明内置运行时的重复 header/query 值以逗号合并；现有原型 Request 是字符串字典，ASGI 仍沿用原型的字典转换。这轮不扩大生产协议兼容声明，S05 必须按真实业务补多值参数/头契约，再决定适配。

验收边界：这证明现有原型的用例与外壳可替换，不证明其他云/PG 已实现、生产业务已等价或部署链路已建。S02/S03、云端性能/现有 Mongo 网络及生产切流均未执行。可通过回退本轮提交恢复原型结构，无业务数据/资源回退。

## 已实际执行

- 拉取 master 至业务基线 `8e4283e5`；随后独立目录命名 PR [#141](https://github.com/makewheels/video-2022/pull/141) 的 `365004da` 仅改需求目录及引用，业务基线不变。
- 读取项目 README、AGENT.md、CONTRIBUTING.md、既有转码设计及验收记录、部署工作流、基础设施仓库历史部署清单。
- 扫描两个 Java 模块的 Controller 映射，产出 routes.md；逐业务检查同步调用、后台执行、内存状态、持久化、临时盘、外部命令和请求返回类型。
- 盘点 6 处有效定时任务和 1 处注释占位；重点读取转码/兜底恢复、两套 Webhook、钱包扣款、OSS 日志/清单全量处理、视频级联删除及香港 YouTube 调用路径。
- 核查官方 FC HTTP/VPC/数据库/资源上限、消息投递和 PyMongo 连接池资料。资料来源见 audit.md 和候选设计末尾。

- 本地文档检查实际通过：59 个本地链接存在；40 个 Controller 均进入清单；139 处方法映射与源码计数一致，展开为 148 条路径；6 个有效 Scheduled 计数一致；代码围栏配对及 `git diff --check` 通过。
- 命名 PR #141 的 [CI 38507189852](https://github.com/makewheels/video-2022/actions/runs/38507189852) 最终 success，转码 Worker、后端/前端/CLI/AI Agent/Android/iOS、质量门禁、构建和汇总门禁通过；API/浏览器 E2E 按流水线条件 skipped，不能说这两项实际跑通。PR 已合并为 `188ea817`；这些 Java/客户端检查不等于 Python 迁移验收。

以上检查只证明文档引用及静态盘点完整，不证明云端可用。

## 未实际执行

| 项目 | 本次状态 |
|---|---|
| Python Handler / FastAPI FC 部署与冷启动 | 入口已实现并做本地初始化对比；未部署 FC，无云端响应时间实测 |
| FC 到现有 Mongo 的连接、网络费用及 VPC | 未配置、未连接；私网可达性未确认 |
| Mongo 实时拓扑、最大输入/批次、现役索引 | 未登录线上核实；历史记录不能当当前实测 |
| 业务接口前后等价、各端 E2E | 现有用例仅为参考覆盖，本次未跑迁移用例 |
| 转码/播放/HDR/历史/YouTube 云端验证 | 本次未跑；既有转码验收不是 Python API 验收 |
| 财务重试一次性扣款、队列投递间隙崩溃 | 仅发现风险，尚未实现证明 |
| 完整 Python 替代应用构建/业务 CI、费用账单 | 隔离原型 CI 已通过，未完成业务替代/完整迁移验收，未创建付费原型资源 |
| 新旧切流、回滚与数据库恢复 | 仅计划，未执行 |

## 后续讨论已确认（2026-10-14）

- 本轮继续使用现有 MongoDB，不更换业务数据模型；未来可能考虑 TableStore，本轮不做该迁移，避免同时扩大改动。
- 响应越快越好，按轻量 Serverless 方式优化。用户未采用建议的 P95 1 秒/300ms 门槛，不再把毫秒数作为等待用户选择的事项；冷启动、Mongo 首连、真实页面和播放体验仍需实测。
- 框架、依赖和可靠任务实现由实施方调查/原型选择，不把技术方法选择交给用户。
- 迁移前后业务、接口和用户用法完全一致，无常驻保活；已有约束继续有效。
- 用户追加确认：外壳/公共业务/云与数据库适配隔离，基于阿里云但保留换云与 PG/Mongo 的替换边界；Git 唯一源码，OSS 仅存可追溯产物；按小阶段、多 PR 推进，持续维护接续记录以应对额度中断。

## S00 规划补充（2026-10-14）

- 重读官方冷启动、运行时、HTTP、实例/并发、数据库、异步任务、资源限制、代码包、版本/别名和计费文档，整理到 official-guidance.md，区分事实、取舍和未实测能力。
- P0–P4 细分为 S00–S24 的小阶段，复杂项继续 a/b 拆 PR；新增 progress.md 记录实际状态和下一步。
- 将可迁移边界和 Git→CI→OSS→FC 来源约束写入候选设计；本次仅文档，S01 分层与 S02/S03 构建发布尚未实现，不将新规则写成已完成验证。

## 待调查与有条件决策

1. 现有 MongoDB 的安全连接、跨云延迟和费用；继续使用 Mongo 没有授权迁库/采购，确需变更时再提交具体证据与方案。
2. 比较内置 Handler/FastAPI 的冷启动和兼容性，确定依赖/网络优化；这不是待用户选择的框架问题。
3. 无持续空轮询时的可靠唤醒/恢复机制；保留原有业务恢复能力，不能靠删扫描“解决”。
4. 财务幂等或可靠调度若需最小技术数据/索引变更，先设计确认；不得改用户字段、账期、金额或同步接口语义。

## S02 本地与 CI 验收（2026-10-14，专项通过）

- 从 `7facb75a` 恢复，完善 Git 白名单、requirements ZIP 绑定、依赖 METADATA 对照和 ZIP 异常拒绝。
- macOS CPython 3.12.12 / uv 0.11.7 实际 Ruff 通过；pytest 77 passed、真实 Mongo 1 skipped、既有 TestClient warning 1；500 行门禁和 `git diff --check` 通过。另在默认 CPython 3.14.4 上跑过同样 77 passed/1 skipped，但它不是正式构建环境。
- 实际验证 pyproject.toml/uv.lock-only 临时快照可执行 `uv export --locked --no-dev --no-default-groups --no-emit-project`；本地工具版本不是固定 Linux builder，不等于正式构建已通过。
- 新测试覆盖可信 SHA/commit、脏树/未跟踪拒绝、Git blob/双包白名单、源码符号链接、危险/重复/坏 ZIP、固定元数据、requirements 与依赖清单篡改、变更源码、依赖注入/输出边界及 CLI 明确拒绝。
- 独立 Linux job 固定 CPython 3.12.12 / uv 0.12.2，待 CI 验证两次 ZIP 摘要及隔离 Handler/ASGI、BSON/Pydantic native 导入。真实 Mongo 仍由原型 job 检查；API/浏览器 E2E 是否跳过单列。
- 未部署 FC，不证明 Debian 11 ABI/实际 FC 网络、性能或生产业务等价。S02 回退到 S01 `4338dfb9` 不涉及业务/数据回退。

- 首轮 [CI 38548555728](https://github.com/makewheels/video-2022/actions/runs/38548555728) packaging 在 host gate 被拒绝：setup-uv 设置 UV_PYTHON 不保证裸 python 版本。改用 `uv run --no-project --python 3.12.12`，保留严格 gate，后续结果待运行。

- 边界复查追加 `.` ZIP 路径拒绝、zlib 损坏错误包装和 CPython 实现限制；本地 Ruff/构建验证测试 35 passed。首轮 Linux 原型 job 78 passed（含隔离 Mongo）、既有 warning 1，后续新 head 完整 counts 待 CI。

### Linux 实际证据

[CI 38548901668](https://github.com/makewheels/video-2022/actions/runs/38548901668)，提交 `22ddda5951f9b86fb8de9f7ae80dba9725a68d58`，Ubuntu 22.04 x86_64 / CPython 3.12.12 / uv 0.12.2：

- packaging success：Handler/Web 各从干净 Git 对象构建两次，ZIP SHA256 各自一致；逐包源码、requirements、METADATA/manifest 校验通过；忽略 site-packages 的新进程中 Handler 和实际 ASGI HTTP 成功，BSON/Pydantic native 扩展导入通过。
- Handler：1,385,323 bytes；SHA256 `d19761ade6bc6b6400370bc2699ab2216267763bd30f18f8e72c4d3af8da659a`；仅 dnspython 2.9.0、pymongo 4.18.3 两项运行依赖。
- Web：4,748,028 bytes；SHA256 `3497049befa1cdd322ec76d50cc5c8977a1413d9d48ba870366b0fa855add5a3`；16 项运行依赖，版本清单见 packaging 日志。测试/dev/媒体工具与另一入口源码未入包。
- Linux 原型 job：Ruff 通过、79 passed（含真实隔离 Mongo）、既有 TestClient warning 1；PR API/浏览器 E2E 依据现有条件 skipped。
- 此证据对应具体代码提交；本次补写文档不进入构建输入。#148 最终 head 仍须全部必要 CI 通过才合并，最终状态查 PR。没有 FC/生产 Mongo/网络/业务迁移验收，后续 S03/S04 继续。

- S02 最终文档 head `8e0e13d2` 的 [CI 38549335594](https://github.com/makewheels/video-2022/actions/runs/38549335594) 实际 success，全部必要 job 通过，API/浏览器 E2E skipped；#148 已合并为 `42a0ebeb`。本阶段实际成果为 Linux 构建来源/回归/双包可重现，FC 发布进入 S03。

## S03 隔离发布（2026-10-14，实施中）

- 实际只读查到本机 Infisical session 已可用、既有部署 RAM 身份允许 FC API、没有 OSS 权限；北京 FC 中尚无 serverless API 函数。检查不等于 Actions OIDC 或新发布已通。
- SDK 隔离解析/方法签名核对：FC SDK 4.8.3、oss2 2.18.4。设计选择独立 OSS 前缀上传/回读验证，再传同一 ZIP 字节，不扩 FC 身份权限，不注入生产 Mongo。
- 工具/workflow/回退测试尚未完成；未发布/上传/调用隔离 FC，不宣称 S03 完成。

### S03 本地工具验收

CPython 3.12.12 / 锁定 web+deployment 组：Ruff 通过，pytest 103 passed、真实 Mongo 1 skipped、既有 TestClient warning 1；500 行门禁和空白检查通过。依赖组解析发现 pycryptodome 4.0.0 只提供 cp315 wheel，限制 <4 后锁定 3.24.1，实际 SDK 安装成功；运行 ZIP 的依赖导出排除 deployment 组。

测试覆盖不可变对象/可信 SHA、包与 OSS 篡改先于 FC 调用、固定隔离配置、拒绝外部 alias/LATEST/手工同名函数、candidate 失败不晋级、dev 失败恢复前版且冒烟、首次失败不假造旧版、可信 receipt 过期拒绝、未鉴权诊断不能通过、master/CI gate、锁定 SDK 的嵌套 body/流式 InvokeFunction 序列化和错误脱敏。这些 SDK/mock 用例不等于 FC 真实发布已成功。

当前尚无 S03 云端发布证据，PR/Linux CI 和 Actions OIDC/OSS 写权限/FC 运行时、同包晋级、实际回退仍待执行。

- S03 PR [#149](https://github.com/makewheels/video-2022/pull/149) 已创建。首轮 [CI 38554283709](https://github.com/makewheels/video-2022/actions/runs/38554283709) 原型与 packaging job 通过，但后续新 head 仍须实际检查全部必要 CI，不能把旧 head 结果冒充新提交验收。
- 云端只读 SDK 实测发现旧 FC endpoint 返回 InvalidVersion，官方 CLI 3.4.11 dry-run 回读实际 FC3 地址 `fcv3.cn-beijing.aliyuncs.com`，修正后对隔离目标 GetFunction 实际返回 FunctionNotFound。ListProvisionConfigs 对该不存在函数也返回 FunctionNotFound；创建后/冒烟前必须继续确认无预留实例配置，不能把缺函数当权限失败或反过来。
- 增加预留配置拒绝/初次发布配置缺失/receipt 保存失败回退回归。修正后的本地完整 104 passed、真实 Mongo 1 skipped、既有 warning 1；再追加故障用例后的发布与 SDK 回归 28 passed，Ruff 通过。尚未上传 OSS、创建/发布 FC。

### S03 工具 PR 最终结果（2026-10-14 22:00 接续）

#149 最新 head `7fc243c4` 的 [CI 38555210822](https://github.com/makewheels/video-2022/actions/runs/38555210822) 实际 success：Linux 原型 107 passed、既有 warning 1；packaging 和全部必要 job 通过。PR API/浏览器 E2E skipped，未冒充通过。已合并为 `2eb0065e94795073f87ca8b9bef30d9784990276`。

合并后的 [master CI 38576710009](https://github.com/makewheels/video-2022/actions/runs/38576710009) 全部 success，API/浏览器 E2E 实际运行通过；既有 Java 部署 38576709748 success。

### S03 首次 Actions 发布实测（22:08–22:16）

[隔离 publish 38577826254](https://github.com/makewheels/video-2022/actions/runs/38577826254) 使用 merge `2eb0065e`：来源 CI 门槛、FC/OSS 两组 Infisical OIDC 取密、Linux 构建和 Handler 冒烟成功；云操作 AccessDenied，workflow failed。

只读 RAM 调查确认 OSS 数据身份 video-2022-oss-data-dev 只允许 GetObject/ListObjects/GetObjectMeta/GetBucketInfo，不允许 PutObject；没有配置专用发布 role。未修改 IAM，也未换高权限身份重试。日志只含错误码，具体拒绝 API 尚无操作级证据。FC GetFunction 实际返回 FunctionNotFound；没有创建隔离函数、版本或别名，也没有真实晋级/回退通过证据。OSS 残留和账单未核实，不称零费用。S03 未全部完成，S04 未部署/未验；先推进独立 S05。

## S05.a 公共视频列表台账（2026-10-14 22:38）

基线 `53f275b8`，逐项核对 Controller/Repository/Service/VO、token/日志/异常管线及 Web、CLI、Android、iOS 调用，新增 [台账](public-video-list-contract.md)。确认字面关键词搜索、仅 PUBLIC 条件、独立 count、签名封面、空值与嵌套映射、GET 请求日志副作用；动态参数错误/日期与脱敏对照工具未验，S05/S07 不标完成。仅文档，git diff --check 通过，不重跑与文档无关的本机完整测试；本 PR CI 待运行。

## S05.c Java 受控 HTTP 夹具（2026-10-14 22:57）

新增 PublicVideoHttpContractTest，完整 WebApplicationContext/MockMvc、真实独立 mongod（随机回环端口）、外部服务既有 mock。JDK 21、TZ=Asia/Shanghai；源码副本、target、日志和 Mongo dbpath 均在系统任务临时目录，finally 终止本次 mongod 并清理。`mvn -q -pl video -Dtest=PublicVideoHttpContractTest -Dspring.mongodb.port=<task-port> -Dspring.mongodb.database=video-public-contract-isolated test checkstyle:check` 最终 **5 passed / 0 failed / 0 skipped**，Checkstyle exit 0，空白门禁通过。

首轮两个预期失败：负 skip 并未报错，Mongo 读取 watch=null 后 watchCount 仍为构造默认 0；依据实际结果修正黄金预期，未改业务。最终验证缺参/非整数/溢出 HTTP 200+code1、异常日志、limit0 不限条数、负 skip 第一页、PUBLIC 非 READY/可见性、字面搜索/空白/大小写/tags、分页/total、匿名/旧 token/无效 token、UTC Date+北京时间字符串、null/历史链接及请求日志。PR CI 尚待；生产、FC、实际签名与完整迁移未验。
