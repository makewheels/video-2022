# 接续记录

额度中断、上下文压缩或更换执行环境后，先读本页，再核对 Git、PR、CI。记录必须随阶段和重要状态更新，不能只在额度用完时补写；不保存密钥、用户数据或临时下载内容。

## 当前快照（2026-10-15 05:17，S06.d3已合并，日志故障语义已本地验证）

- S02 [#148](https://github.com/makewheels/video-2022/pull/148) 已合并，merge `42a0ebeb0a2865adf3c6e1d8791bb276d970d8e9`；最终 [CI 38549335594](https://github.com/makewheels/video-2022/actions/runs/38549335594) success，所有必要 job 通过，API/浏览器 E2E 按 PR 条件 skipped。Linux 原型 79 passed，双包两次可重现、入口与 native 冒烟通过。
- S03 工具 [#149](https://github.com/makewheels/video-2022/pull/149) 已合并，merge `2eb0065e94795073f87ca8b9bef30d9784990276`；最新 head `7fc243c4` 的 [CI 38555210822](https://github.com/makewheels/video-2022/actions/runs/38555210822) success，Linux 原型 107 passed/1 warning，全部必要 job 通过、PR 两个 E2E skipped。云端发布与回退仍未验，不能称 S03 全部完成。
- 验收文档 [PR #150](https://github.com/makewheels/video-2022/pull/150) 已合并为 `53f275b8`。S05.a [PR #151](https://github.com/makewheels/video-2022/pull/151) 最新 head `20a71229` 的 [CI 38581828673](https://github.com/makewheels/video-2022/actions/runs/38581828673) success 后合并为 `33b1a68f`；PR E2E 按既有条件 skipped。
- S05.b [PR #152](https://github.com/makewheels/video-2022/pull/152) 最新 head b479d27b 的 CI 38584373965 全部必要 job success 后合并为 `5cf8716c`，PR E2E skipped；本地 25 项离线对照回归/Ruff/500 行通过。
- S05.c [PR #153](https://github.com/makewheels/video-2022/pull/153) 最新 head `6d9cdbcb` 的 [CI 38586272991](https://github.com/makewheels/video-2022/actions/runs/38586272991) 全部必要 job success 后已合并为 `7ff9a3f1`；PR E2E skipped。首 CI 日期失败已修正，UTC 本机551/北京时间focused5通过，最终 Linux 后端也通过；具体计数/证据保留 verification。
- S05.d [PR #154](https://github.com/makewheels/video-2022/pull/154) a1fcefd1 的 CI38587849569 必要项success后合并为fc54aee1；PR E2E skipped。
- S05.e [PR #155](https://github.com/makewheels/video-2022/pull/155) 最新 head f8fe8541 的 CI38589941727 全部必要job success后合并为12092139；PR E2E skipped。登录HTTP6项/Checkstyle已过，生产判定/TTL清理/并发未验。
- S03.b [PR #156](https://github.com/makewheels/video-2022/pull/156) 01198f60 的 CI38591679417 全部必要job success后已合并8a6b5304（PR E2E skipped）。候选role/trust仅配置，未创建/代入，真实发布回退仍阻塞。
- S05.f [PR #157](https://github.com/makewheels/video-2022/pull/157) a656f79c 的 CI38594738912 全部必要job success后合并fbb8502a（PR E2E skipped）。真实生产判定配置/索引夹具3passed，未验证实际生产配置/TTL删除时机/并发。
- S06.a #158已在全部必要CI38597432860成功后合并39af8559（PR E2E skipped）；[旧用户读取能力](user-reads.md)：纯端口/用例/HTTP值编码，4份固定Java HTTP与Python对照已过，完整Python165passed/Mongo1skipped，Java4passed/Checkstyle/Ruff/500行/空白通过。没有Mongo用户适配器或应用业务路由，不能称用户API已在FC迁移。
- S06.b1 #159 a9933666 的CI38620516871全部必要job成功后合并`286c3e7d`（PR E2E skipped），Java3项基线已固化。
- S06.b2 #160 f5640c6a 的CI38622565458必要项全通过后合并9fa11d75（PR E2E skipped）；Mongo只读适配器176passed，未装配HTTP，未验证BSON类型/完整请求日志/生产网络仍为门槛。
- 频道测试修复#161 head748bdd06的CI38624666455必要项全通过后合并2d9cf7f0（PR E2E skipped）；#160合并master CI38624255599/部署38624255442均success。
- S06.c1 #162 head37ce40db必要CI38626919450全通过后合并f98e91e2（PR E2E skipped），头像匿名鉴权缺口已记录，未修业务。
- S06.c2 #163 headd0ed7984必要CI38628826927全通过后合并104d54a7（PR E2E skipped）。#162合并master CI38628443843/部署38628443893均success。
- S06.c3 #164 head61ff5e55必要CI38630860497全通过后合并0ca7e09c（PR E2E skipped）；#163合并master CI38630544702/部署38630544827均success。
- S06.d1 #165 headc4f77f86必要CI38633115869全通过后合并e514ff69（PR E2E skipped）；#164合并master CI38632657026/部署38632657032均success。
- S06.d2 #166 head063a0e1d必要CI38635180591全通过后合并2017e3e5（PR E2E skipped）；#165合并master CI38634877692/部署38634877557均success。
- S06.d3 #167 head4f8d788f必要CI38637312136全通过后合并175ff4eb（PR E2E skipped）；#166合并master CI38636953114/部署38636952960均success。
- 当前分支`test-serverless-request-journal-failure`基于175ff4eb，S06.d4 [日志故障基线](request-journal-failure.md)：真实Spring/隔离Mongo+指定方法注入异常，3passed/Checkstyle。日志save失败保留成功响应/业务写，但RequestLogContext残留，鉴权拒绝不清它；身份查询先失败则无日志。仅测试记录，未修运行代码，Python同步日志尚未装配。
- #158合并后master CI38620293672失败：浏览器频道等待选择器strict mode匹配2元素，79passed/1failed；既有Java部署38620293649成功。#159合并master CI38622137273已success（实际API/浏览器E2E通过）；部署38622137369另查询。旧失败仍有确定的并存元素证据，独立修复测试竞态。

- 本机 Git 直接访问 GitHub 临时失败，使用当前系统代理 127.0.0.1:7890 的单次 git 参数已 fetch 至合并提交；没有修改全局代理/Git 配置。
- 实际读取：本机 Infisical session 现在可用；prod `/` 存在 FC_DEPLOY_AK_ID/SECRET，`/oss` 存在数据 OSS 身份；dev 没有这些部署键。部署 RAM 用户 video-2022-fc-deploy 的已有 policy 允许 FC 函数/版本/别名/触发器/并发相关 API，无 OSS 权限。只读 FC 列表没有 serverless API 函数。密钥仅在内存/受限系统临时配置，未写 Git/报告。
- S03 将由 Actions 复用现有 OIDC，独立 OSS 前缀上传/回读后把已验证 ZIP 字节交给 FC，避免扩大现有 RAM 权限。仍需验证 Actions 权限、实际发布、同包晋级/回退。生产与 Mongo 保持现状。
- 用户新授权：夜间自主接续至 2026-10-15 08:00；06:05 起在浏览器打开 HTML 汇报。macOS launchd 已注册，同一会话每 15 分钟接续，账户额度不足等待重试；报告独立每 5 分钟更新；配置在 `~/Library/Application Support/video2022-night-run-20261014/`，报告为 `~/Documents/video2022-2026-10-15-夜间进度.html`。注册/语法/限额查询/HTML生成已验，实际触发运行记录查该目录日志。
- 当前稳定检查点：本地 CPython 3.12.12 + 锁定 web/deployment 组，Ruff 通过、103 passed、真实 Mongo 1 skipped、既有 warning 1，500 行/空白门禁通过。SDK 间接依赖 pycryptodome 4.0.0 仅发布 cp315 wheel，已限制 <4 并锁定可用 3.24.1；API ZIP 仍排除此组。
- 调度实际修复：18:45 日历已触发，但独立 CLI resume 遇到现有 writer 冲突；改成共享本机服务 Unix WebSocket RPC，18:52 实测原会话 turn/start 送达，工作中 heartbeat 跳过不并行。19:00 实际 tick 正常跳过；限额/线程读回已验。
- 云端只读检查发现旧 `fc.cn-beijing` 入口返回 InvalidVersion；用官方 CLI 3.4.11 dry-run 回读目标 `fcv3.cn-beijing.aliyuncs.com` 后修正 SDK，并实际得到目标 FunctionNotFound。ListProvisionConfigs 对不存在函数也返回 FunctionNotFound，按不存在处理；创建后及各次冒烟继续确认无预留配置，不忽略 Forbidden。最新本地完整 104 passed/1 skipped/1 warning，随后发布回归 28 passed，Ruff 通过。
- 实际隔离 publish [38577826254](https://github.com/makewheels/video-2022/actions/runs/38577826254) 对 `2eb0065e` 失败：CI 来源门槛、两组 Infisical Actions OIDC 取密、Linux 构建/入口冒烟通过，云操作返回 AccessDenied。只读核对 OSS 身份 video-2022-oss-data-dev 的 Custom policy 仅允许 GetObject/ListObjects/GetObjectMeta/GetBucketInfo，无 PutObject；附加 STS AssumeRole 权限不等于对象写入权限，当前没有配置发布专用 role。FC 身份无 OSS 权限。没有扩权或更换生产身份重试。发布日志没有操作级上下文，不能仅凭错误断定具体被拒绝 API；现有身份缺少上传权限已证实。
- 发布后只读 GetFunction 返回 FunctionNotFound，隔离 FC 未创建；没有 version/alias/receipt，真实晋级与回退未执行。23:21现有读取身份实际分页列该提交前缀为0对象；不推断其他前缀或账单。拟议隔离role的只读GetRole返回EntityNotExist.Role，未创建或扩权。S03/S04 保留阻塞，下一独立阶段为 S05 契约台账。

## 用户约束（接续不能丢失）

- 全部现有业务、接口、用户操作及历史播放一致，不新增需求外业务字段或规则。
- 轻量 Python、无常驻保活、响应越快越好；没有批准具体毫秒阈值，不重复要求选框架/数值。
- 本轮继续现有 Mongo，TableStore 只属未来可能性；未授权迁库位置或采购固定资源。
- 外壳与业务隔离，基于阿里云但不绑定公共逻辑；将来可替换云和 PostgreSQL/Mongo 适配器。
- Git 唯一源码，发布从 Git 经 CI 单向构建/发布；允许 OSS 存产物，禁止只在 OSS/控制台存在自研源码。
- 小阶段、多 PR、持续记录接续；已授权范围内自主推进，不在每阶段重新问是否开始。

## 当前阻塞与独立工作

- 本机 Infisical session 和 Actions OIDC 已实际成功；OSS 发布写权限阻塞已证实。后续需可审阅的隔离前缀最小权限发布身份/role 配置及 Actions 验证，不能把读取身份升级为全 bucket 写身份。当前不擅自改 RAM、不反复重试同一已知无写权限身份，先推进 S05。
- 现有 Mongo 的跨云访问、最小权限和真实 FC 冷启动未验；不擅自开放公网/改防火墙/搬库。
- 本机 Docker daemon 不可用；CI 隔离 Mongo 已验证，不能因此声称生产网络可达。
- Mongo 写库/投递间隙尚未解决；S01/S02/S05 可以先推进，不绕过恢复要求。

## 接续步骤

1. #152至#167均已合并，不重复。先核对test-serverless-request-journal-failure独立PR/head/必要CI再合并S06.d4。后续基于故障基线补普通日志端口/请求生命周期，或FC元数据/参数合并；不把日志失败改成业务500/重试业务写，不开放半成品路由。
2. 当前 master CI/E2E 已通过，但首次 publish 因 OSS 写权限失败；先解决独立发布身份权限并经审阅/验证，再运行 `gh workflow run deploy-serverless.yml --ref master -f mode=publish`；锁定实际 run 的 head，等待并检查全程日志。失败按真实错误在独立 fix 分支修复，再经 PR/CI/master CI 重试；不把旧 head 通过当新提交证据。
3. publish 成功后保存非秘密 receiptKey、commit、ZIP SHA、version、同包晋级/远端回读结果。首次没有前版不能假造回退；第二次可追溯发布后用新版 receiptKey 执行 workflow rollback，实际读回 alias/version 与冒烟。
4. S03 通过后才开始 S04 FC HTTP/冷热/原生扩展/现有 Mongo 网络；不擅自开放数据库公网、改防火墙、迁库或采购固定资源。无法安全接通则记录阻塞，推进独立 S05 契约台账。
5. 所有时间判断用实际北京时间。06:05 后运行调度报告并打开浏览器；07:45 后不开始大阶段，08:00 停止并保存检查点。额度不足时先保存、push，再由定时接续重试。

## 已查依据与未确认边界

- 阿里云 Python 运行时：https://help.aliyun.com/zh/functioncompute/python3/ 。查阅时 Python 3.12 为公测、Debian 11 x86_64；具体地域与实际 FC 配置仍需核验。
- Python 代码包：https://help.aliyun.com/zh/functioncompute/deploy-a-code-package-1 ；第三方依赖：https://help.aliyun.com/zh/functioncompute/install-third-party-dependencies-for-a-function 。依赖需与目标 Linux/架构匹配，不能上传 Windows wheel。
- uv 导出：https://docs.astral.sh/uv/concepts/projects/export/ 。候选 wheel target 为 `x86_64-manylinux_2_28`。
- 本机 Docker daemon 不可用；远程查询候选 `python:3.12.12-slim-bullseye` 镜像返回 not found。因此未验证 Debian 11 容器；Linux runner 冒烟也不能替代 FC/Debian 11 实测。若需要容器，先确认真实存在且合适的镜像，不假称已通过。
- S03/S04 才核实云资源、网络/权限、真实冷/热与 Mongo 首连。隔离发布已尝试但 FC 未创建；未核实 OSS 残留或账单，不能断言零费用。manifest 是完整性记录，不是数字签名，后续发布必须从受控 CI 获得可信摘要。
- S03 工具回退点为 S02 merge `42a0ebeb`；运行时回退以受控 receipt 的前版本为准，首次没有旧版本。现役 Java 业务未替换或切流。

## 检查点规则

- 稳定小改动完成时写本地 commit；准备 PR、长操作或中断前推送可接续分支，记录提交、PR、未提交改动及下一条操作。
- 记录命令、结果/CI 链接和必要摘要，明确 passed/skipped/pending/failed/未运行；不复制巨大日志或秘密。
- 半成品留在工作分支并标注，不把未跑测试的阶段标完成。CI 等待时不无理由重跑全套或不断提交状态刷新。
- 接续先 `git status --short`、fetch、查询 PR head/CI；未提交改动先确认归属，禁止 reset/覆盖后再调查。
- 阶段收尾写“实际成果、剩余项、回退点、下一阶段”；系统临时目录不是唯一工作记录。不承诺额度恢复后平台自动执行，恢复工作以此记录为准。

## 当前执行补充（2026-10-14 23:14，S05.e）

- 当前分支 `test-serverless-login-http-contract`，基于已合并 S05.b `5cf8716c`；S05.c #153 已合并，S05.d #154 已合并为 fc54aee1，最新查询为准。不能合并未完成 CI。
- S05.e [登录 HTTP 夹具](login-http-fixtures.md) 6 项集成测试+Checkstyle/空白通过，测试是真实隔离 Mongo/完整管线、固定身份；生产配置/TTL清理/并发未验。
- 此历史执行补充已完成：#154/#155均合并，当前以页首S03.b快照为准。不要回到已合并 #149/#152 重复实现。

## 当前执行补充（2026-10-14 23:55，S05.f）

- 分支 `test-serverless-production-login-contract`，基于 master12092139，独立补生产判定与TTL索引HTTP夹具；真实临时Mongo/真实EnvironmentService配置绑定，3passed/Checkstyle/空白通过，临时进程/目录清理。
- S03.b #156 已合并；已取消被替代旧head e297348c 的同PR CI38591558651，避免旧run占用并阻塞新head；没有取消masterCI或部署，不沿用旧head验收。
- S05.f #157 已合并fbb8502a；此为历史补充，以页首快照为准。TTL后台删除/生产部署/并发仍待，不使用生产账号或真实验证码。
