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

## S05.b 离线响应对照工具（2026-10-14 22:51）

实现 [compare_responses.py](../../../serverless/contracts/compare_responses.py)，仅受控 JSON HTTP 夹具。严格字段/类型/null/数组顺序与 HTTP/业务错误比较，coverUrl 有限签名值归一化保留对象/身份/签名存在性，所有输出无响应值/未知字段名/文件路径。CPython 3.12.13 + uv 锁定临时环境：Ruff 通过，25 项 focused 用例通过，500 行/空白门禁通过；环境/缓存/测试夹具清理。首次 CPython 3.14.4 的 24 项也通过，补充有限数值边界后在 3.12 重验。CI 待跑；工具匹配不代表签名有效/生产兼容，Java 动态黄金夹具和 Python 业务均未实现。

## S05.c Java 受控 HTTP 夹具（2026-10-14 22:57）

新增 PublicVideoHttpContractTest，完整 WebApplicationContext/MockMvc、真实独立 mongod（随机回环端口）、外部服务既有 mock。JDK 21、TZ=Asia/Shanghai；源码副本、target、日志和 Mongo dbpath 均在系统任务临时目录，finally 终止本次 mongod 并清理。`mvn -q -pl video -Dtest=PublicVideoHttpContractTest -Dspring.mongodb.port=<task-port> -Dspring.mongodb.database=video-public-contract-isolated test checkstyle:check` 最终 **5 passed / 0 failed / 0 skipped**，Checkstyle exit 0，空白门禁通过。

首轮两个预期失败：负 skip 并未报错，Mongo 读取 watch=null 后 watchCount 仍为构造默认 0；依据实际结果修正黄金预期，未改业务。最终验证缺参/非整数/溢出 HTTP 200+code1、异常日志、limit0 不限条数、负 skip 第一页、PUBLIC 非 READY/可见性、字面搜索/空白/大小写/tags、分页/total、匿名/旧 token/无效 token、UTC Date+北京时间字符串、null/历史链接及请求日志。PR CI 尚待；生产、FC、实际签名与完整迁移未验。

### S05.c 首次 Linux CI 差异（23:05）

#153 CI 38585341779：后端 630 项，新增 HTTP 夹具 1 项日期失败（预期 11:04:05，实际 03:04:05）。原因是 DateUtil 在此前测试初始化并缓存 UTC 格式器，测试内改 TimeZone 不代表重新初始化进程。移除全局时区修改，按启动 ZoneId 用独立 JDK 日期格式计算期望，保持 UTC JSON 编码不变；正在分别复验 UTC 全后端与北京时间 focused，失败 head 不合并。业务与生产配置未改。

时区修正复验完成：JDK 21、真实临时 Mongo；UTC 下本机全后端 Maven/Surefire 3.5.4 汇总 **551 passed / 0 failed / 0 skipped**，Checkstyle exit 0；另一个北京时间 JVM `-Dtest=PublicVideoHttpContractTest` **5 项 focused 通过**。第二次复用临时 target，汇总目录保留第一轮报告，不能称第二轮也跑了 551。CI 使用其既有命令/测试发现方式，首轮 630 不等同本机 551；新 head Linux 全套仍须通过。所有本次临时文件/进程已清理。

## S05.d 登录/token/观看身份台账（2026-10-14 23:13）

核对 UserController/Service/Repository、VerificationCode/User、client/session、环境判定、钱包及 Web/CLI/Android/iOS 调用；六入口的参数、错误码、TTL/旧 token/副作用已记录。纠正已有 API 文档 Redis 描述，未改代码。iOS LoginScreen 的 /user/initClient、/user/initSession 未在当前 Java Controller 声明中找到，Keychain 生命周期也与 Web 存储不同；记录为差异/待动态验，不把源码盘点冒充全端登录通过。git diff --check 通过；本 PR 仅文档，不重复本机全套测试，必要 CI 待验。

S05.c 最终 Linux [CI 38586272991](https://github.com/makewheels/video-2022/actions/runs/38586272991) 对 6d9cdbcb 全部必要 job success，PR API/浏览器 E2E skipped，#153 合并为 7ff9a3f1。S05.e 独立测试分支已保存15cceab6（6项focused通过），不混进本台账PR。

## S05.e 登录与观看身份真实 HTTP 夹具（2026-10-14 23:14）

新增 LoginHttpContractTest，JDK21/UTC、完整 HTTP/真实随机回环端口 Mongo、既有外部 mock：`mvn -q -pl video -Dtest=LoginHttpContractTest -Dspring.mongodb.port=<task-port> -Dspring.mongodb.database=video-login-contract-isolated test checkstyle:check` 实际 **6 passed / 0 failed / 0 skipped**，Checkstyle/空白通过。测试覆盖验证码复用/创建时间、码11/12、错误不消耗、正确登录覆盖旧token/清码/保留12.34钱包余额、非生产111先需记录且创建钱包、公开用户响应token清空但库仍有效、401无data/头优先query、client/session每次持久化IP/UA。所有源码/target/dbpath/log在系统任务临时目录，finally结束mongod并清理；生产判定/TTL删除时机/并发恢复未验，CI待跑。

### S03 对象残留只读核验（23:21）

现有 OSS 数据身份通过 oss2.ObjectIterator 完整分页列出 `video-2022-dev/serverless/releases/2eb0065e94795073f87ca8b9bef30d9784990276/`：0对象。没有写入/删除或扩大权限；不推断其他前缀或整个bucket无残留，账单仍未验。独立拟议角色 video-2022-serverless-release-oss 的只读 GetRole 实际 EntityNotExist.Role。身份/网络阻塞仍成立，未部署FC/未执行真实回退。读取环境/缓存/密钥仅在受限系统临时任务与内存中，已清理。

S05.d #154 最新 a1fcefd1 的 CI 38587849569 success 后已合并为 fc54aee1；S05.e #155 首 f2c184e4 的 CI 38588504819 全部必要job success（PR E2E skipped），同步master文档后等待新head CI，不把旧CI代替新head。

## S03.b OSS 上传前提与候选权限（2026-10-14 23:37）

新增上传前versioning原始XML门禁和精确role/trust配置，未应用IAM。锁定oss2公开解析器对namespaced Enabled/Suspended返回None，真实SDK样本回归证实，因此不用它判断安全状态；仅复用认证请求路径并有界/严格解析官方XML。CPython3.12.13/web+deployment锁定临时环境完整回归 **155 passed / Mongo 1 skipped / 既有warning1**，Ruff/500行通过。首轮暴露状态/namespace假设失败已修正，最后补充属性式未知状态拒绝用例后，focused **25 passed**，Ruff/500行/空白门禁通过。临时环境已清理，无云端发布/身份代入/回退证明。候选角色缺失和OSS 0对象证据在S05.e记录/PR #155中，最新head CI仍需查询。

S05.e #155 最新f8fe8541 的 CI38589941727 全部必要项success后合并12092139，PR E2E skipped。S03.b 上传保护/候选权限已同步该master，未改变IAM或生产bucket，实际云端权限和晋级/回退仍阻塞。

## S05.f 生产判定与 TTL 索引（2026-10-14 23:55）

JDK21/UTC、真实随机回环端口 mongod、完整 HTTP、EnvironmentService 未mock：ProductionLoginHttpContractTest **3 passed / 0 failed / 0 skipped**，Checkstyle/空白通过。TestPropertySource 将实际读取的配置设prod，同时显式固定回环测试URI/库，清库前确认名称，未读真实生产Mongo。验证码111被拒且保留记录，1234成功清码；实际实体注解索引在隔离库创建/回读：verification_codes、phone唯一、createdAt expireAfterSeconds=600。配置/索引证据不等于TTL后台删除或FC生产部署；并发恢复未验。构建/日志/dbpath均系统临时目录，finally结束进程并清理；CI尚待。

S03.b #156 最新01198f60 的 CI38591679417 全部必要项success，合并8a6b5304，PR E2E skipped；仅工具/候选权限配置验收，真实IAM/发布仍未通过。S05.f #157同步该master后需要新head CI。

## S06.a 旧用户读取用例/端口与黄金对照（2026-10-15 00:20）

新增纯Users/UserReads/UserSnapshot和通用HTTP值编码，按旧token/公开ID读取；返回快照清空token不写库，原始token不规范化，存储异常不伪装为未找到，日期UTC毫秒/13字段/null保留。未装配Mongo适配器或业务路由，原型仍404这些路径，不称完整用户API迁移。

Git Java测试在真实临时Mongo/完整HTTP/JDK21/UTC生成4份固定响应，4passed/Checkstyle通过。首对照4项失败来自导出器默认遗漏Map null，已改显式保留并断言回读与实际body一致，重新生成后4份Python对照通过；未改变Java业务或删掉Python空字段。锁定CPython3.12.13 web+deployment临时环境完整 **165 passed / Mongo1 skipped / 既有warning1**，Ruff/500行/空白通过，架构依赖门禁/原接口404保护通过。源码副本/依赖环境/target/dbpath/log均清理，持久的4份合成样本在serverless/contracts/user-read；CI待验，生产/FC网络/完整日志管线未验。

## S06.b1 旧用户原始 BSON 基线（2026-10-15 03:05）

- 基于master39af8559，真实回环独立临时mongod，源码/构建/日志/数据库均系统任务临时目录，finally终止自建进程并清理。JDK21/TZ=UTC，mvn -q -pl video -Dtest=UserMongoReadContractTest（临时Mongo端口/库覆盖）test checkstyle:check：3 tests / 0 failures / 0 errors / 0 skipped，退出0。
- 缺失字段保留构造默认，显式null清空；ID和数值转换见[user-mongo-matrix.md](user-mongo-matrix.md)。初稿null假设失败，标记逐样本复核后修正断言；未修改运行代码。测试后仅移除诊断输出/改准确方法名。
- S06.a #158必要CI38597432860全部通过，PR API/浏览器E2E按条件skipped，已合并39af8559；合并master CI38620293672及既有Java部署38620293649当前运行中。本阶段未验证Python适配器/生产数据/FC网络，不重复失败发布。

## S06.b2 Mongo只读适配器（2026-10-15 03:18）

- 本地CPython3.12.13，锁定web/deployment组，系统任务临时env/cache/pytest/dbpath；独立自建回环mongod，finally终止进程/清理目录，测试独立随机数据库finally删除。完整pytest：176passed/0skipped/1既有Starlette warning，3.74s。Ruff/500行/空白通过。初次仅import排序门禁失败，修正后才跑完整套件。
- 新增10项Python测试：真实Mongo四份Java黄金响应、缺失/null、ObjectId不回退、数字/类型敏感token、错误传播/惰性、未验证BSON固定消息。完整套件含既有真实Mongo诊断测试；没有用skip代替本地数据库通过。
- S06.b1 #159 CI38620516871必要项全部通过后合并286c3e7d，PR E2E skipped。#158合并master CI38620293672：浏览器79passed/1failed，频道加载等待locator strict mode匹配2元素；Java部署38620293649 success。最新master CI38622137273仍须查询，不将PR跳过的E2E写成通过。
- 未装配路由/生产Mongo/FC网络；不支持的BSON仍为兼容缺口而非新增业务规则，详见user-mongo-reads.md。

## 独立频道浏览器等待修复（2026-10-15 03:33）

- master39af8559的CI38620293672实际失败日志显示同一页loading div与错误toast同时存在，or_匹配2元素导致strict mode。后续master286c3e7d的CI38622137273和部署38622137369均success，说明该失败并非每次出现，不掩盖旧结果。
- 按[Playwright Locator.or_官方说明](https://playwright.dev/python/docs/api/class-locator#locator-or)处理多匹配，并先filter(visible=True)避免隐藏的第一候选挡住可见状态。保留三个候选和原10秒等待，仅改test/browser/test_channel.py，不改业务。
- 本机Playwright1.58.0+独立headless系统Chrome，实际调用修改前/后的测试方法（导航用固定UTF-8 HTML拦截，不访问生产）：旧写法在loading+toast共存确定复现strict mode；新写法loading/error/content单独、两者共存、首候选隐藏共5种状态通过；无候选仍TimeoutError。此为定位器定向浏览器验证，不是完整应用E2E。AST/500行/空白通过。
- 临时验证程序首轮回调参数误绑定Request、第二轮缺UTF-8 charset均失败，修正验证程序后上述结果才通过；没有为修复验证程序修改应用或继续放宽断言。依赖/env/缓存均任务系统临时目录并finally清理，浏览器/page均finally关闭，无持久回归脚本。
- S06.b2 #160 f5640c6a，CI38622565458必要项全通过后合并9fa11d75；PR API/浏览器E2E skipped。其master CI38624255599/部署38624255442仍须按最新查询，不沿用其他head证据。

## S06.c1 资料/频道/头像 HTTP 基线（2026-10-15 03:49）

- 基于master2d9cf7f0，JDK21/UTC，继承BaseIntegrationTest，真实Spring MockMvc/自建回环临时mongod，OSS为既有mock；源码/target/log/dbpath均系统任务临时目录，finally终止自建进程/清理。mvn -q -pl video -Dtest=ProfileAvatarHttpContractTest（临时Mongo端口/库覆盖）test checkstyle:check：6tests/0failures/0errors/0skipped，5.119s，退出0。
- 覆盖UTF-16长度/无trim/缺失字段清空、受保护资料401、频道与本人资料签名24h/历史回退/不写回token与URL、头像重复创建、凭证类型与owner顺序/31/33/34、完成对象检查与重复通知、现有匿名头像路由缺口。无实际STS或OSS上传，全部固定合成记录。
- 初稿用模型save(null)构造计数样本时本人资料实际返回0，与原始BSON显式null基线不一致；明确用原始BSON $set null构造后5项通过，再加入匿名路径实际验证，最终6项通过。没有修改业务或降低null断言。
- #161 head748bdd06 CI38624666455必要项全通过后合并2d9cf7f0，PR E2E skipped。#160合并master CI38624255599和部署38624255442均success；新master CI38626349934/部署38626350038须查最终结果。
- 见[profile-avatar-contract.md](profile-avatar-contract.md)。本阶段只固化旧接口，Python对应业务/完整请求日志、客户端设备、生产凭证与云网络均未验；S06未完成。

## S06.c2 资料/频道纯读取与Java黄金对照（2026-10-15 04:03）

- 基于masterf98e91e2，扩展既有UserReadGoldenHttpContractTest；JDK21/UTC/真实Spring MockMvc及回环独立临时Mongo，OSS仍mock。9tests/0failure/0error/0skip，5.044s，Checkstyle退出0。任务临时goldens导出5份profile/channel固定响应并保留null，原4份user-read不改；持久目录只格式化新5份合成夹具。
- 本地CPython3.12.13，锁定web/deployment组；完整Python193passed/0skipped/1既有Starlette warning，3.74s；Ruff/500行/空白通过。新增纯用例对照/边界13项和真实Mongo用户读取+替代头像端口组合4项。签名24h、旧URL回退、计数/null及不写回保持；签名器返回null不偷偷回退。
- 源码/target/log/dbpath/env/cache/pytest均系统任务临时目录，finally终止自建mongod及清理；Python fixture独立随机数据库finally删除。无生产数据/OSS/STS请求。
- #162 head37ce40db CI38626919450必要项全过后合并f98e91e2；PR E2E skipped。其master CI38628443843/部署38628443893仍运行中须查最终。先前频道修复master CI38626349934/部署38626350038均success。
- 未实现头像具体适配器、完整HTTP鉴权/日志/异常语义、更新资料或实际FC网络；纯能力未装配，不能称生产迁移。详见profile-channel-reads.md。

## S06.c3 头像文件Mongo读取（2026-10-15 04:17）

- 基于master104d54a7，UserMongoReadContractTest新增File ID/key基线，JDK21/UTC/真实回环独立Mongo：5tests/0failure/0error/0skip，4.774s，Checkstyle退出0。验证物理集合file、ObjectId优先无String回退、不过滤type/owner/deleted、null/空白/整数key。
- 同一自建临时Mongo继续跑CPython3.12.13锁定web/deployment组：完整201passed/0skipped/1既有Starlette warning，3.75s，Ruff/500行/空白通过。新增8项头像文件读取边界/错误/惰性测试；4份profile/channel黄金组合从替代文件端口升级到真实user+file集合，签名仍替代。回读文件/用户确认无写回。
- 源码/target/log/dbpath/env/cache/pytest均系统任务临时目录，finally终止自建mongod并清理；Python随机独立数据库finally删除，测试只允许无凭据回环standalone。无生产Mongo、OSS或STS请求。
- #163 headd0ed7984 CI38628826927必要项全通过后合并104d54a7，PR E2E skipped；#162合并master CI38628443843/部署38628443893均success；新master CI38630544702/部署38630544827仍运行中须查最终。
- 未装配路由/实际签名，未穷尽File其他字段/异常BSON转换，完整HTTP上下文/日志/生产网络未验。见avatar-mongo-reads.md，不能称S06完成。
