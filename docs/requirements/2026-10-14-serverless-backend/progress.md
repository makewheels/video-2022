# 接续记录

额度中断、上下文压缩或更换执行环境后，先读本页，再核对 Git、PR、CI。记录必须随阶段和重要状态更新，不能只在额度用完时补写；不保存密钥、用户数据或临时下载内容。

## 当前快照（2026-10-14 19:14，S02 已合并，S03 PR 验收中）

- S02 [#148](https://github.com/makewheels/video-2022/pull/148) 已合并，merge `42a0ebeb0a2865adf3c6e1d8791bb276d970d8e9`；最终 [CI 38549335594](https://github.com/makewheels/video-2022/actions/runs/38549335594) success，所有必要 job 通过，API/浏览器 E2E 按 PR 条件 skipped。Linux 原型 79 passed，双包两次可重现、入口与 native 冒烟通过。
- 当前分支 `feat-serverless-isolated-release`，基于 S02 merge；S03 设计、发布工具、独立 workflow 与回退/真实 SDK 请求序列化测试已实现；本地验证通过，[S03 PR #149](https://github.com/makewheels/video-2022/pull/149) 已创建，等待最新 head CI/实际发布。未发布 FC/OSS。
- 本机 Git 直接访问 GitHub 临时失败，使用当前系统代理 127.0.0.1:7890 的单次 git 参数已 fetch 至合并提交；没有修改全局代理/Git 配置。
- 实际读取：本机 Infisical session 现在可用；prod `/` 存在 FC_DEPLOY_AK_ID/SECRET，`/oss` 存在数据 OSS 身份；dev 没有这些部署键。部署 RAM 用户 video-2022-fc-deploy 的已有 policy 允许 FC 函数/版本/别名/触发器/并发相关 API，无 OSS 权限。只读 FC 列表没有 serverless API 函数。密钥仅在内存/受限系统临时配置，未写 Git/报告。
- S03 将由 Actions 复用现有 OIDC，独立 OSS 前缀上传/回读后把已验证 ZIP 字节交给 FC，避免扩大现有 RAM 权限。仍需验证 Actions 权限、实际发布、同包晋级/回退。生产与 Mongo 保持现状。
- 用户新授权：夜间自主接续至 2026-10-15 08:00；06:05 起在浏览器打开 HTML 汇报。macOS launchd 已注册，同一会话每 15 分钟接续，账户额度不足等待重试；报告独立每 5 分钟更新；配置在 `~/Library/Application Support/video2022-night-run-20261014/`，报告为 `~/Documents/video2022-2026-10-15-夜间进度.html`。注册/语法/限额查询/HTML生成已验，实际触发运行记录查该目录日志。
- 当前稳定检查点：本地 CPython 3.12.12 + 锁定 web/deployment 组，Ruff 通过、103 passed、真实 Mongo 1 skipped、既有 warning 1，500 行/空白门禁通过。SDK 间接依赖 pycryptodome 4.0.0 仅发布 cp315 wheel，已限制 <4 并锁定可用 3.24.1；API ZIP 仍排除此组。
- 调度实际修复：18:45 日历已触发，但独立 CLI resume 遇到现有 writer 冲突；改成共享本机服务 Unix WebSocket RPC，18:52 实测原会话 turn/start 送达，工作中 heartbeat 跳过不并行。19:00 实际 tick 正常跳过；限额/线程读回已验。
- 云端只读检查发现旧 `fc.cn-beijing` 入口返回 InvalidVersion；用官方 CLI 3.4.11 dry-run 回读目标 `fcv3.cn-beijing.aliyuncs.com` 后修正 SDK，并实际得到目标 FunctionNotFound。ListProvisionConfigs 对不存在函数也返回 FunctionNotFound，按不存在处理；创建后及各次冒烟继续确认无预留配置，不忽略 Forbidden。最新本地完整 104 passed/1 skipped/1 warning，随后发布回归 28 passed，Ruff 通过。
- 下一步：查询 #149 的最新 head CI（首次 38554283709 不代表后续修正 head）；全部必要 CI 通过合并，再等待对应 master CI success 后 dispatch `deploy-serverless.yml` 的 publish。第一次没有前版，不虚构实际回退；需要第二次可追溯发布后用新版 receipt 实测恢复前版。按云端真实错误修复，不因 mock/SDK 校验声称已发布。若 FC/Mongo 网络阻塞则推进 S05。

## 用户约束（接续不能丢失）

- 全部现有业务、接口、用户操作及历史播放一致，不新增需求外业务字段或规则。
- 轻量 Python、无常驻保活、响应越快越好；没有批准具体毫秒阈值，不重复要求选框架/数值。
- 本轮继续现有 Mongo，TableStore 只属未来可能性；未授权迁库位置或采购固定资源。
- 外壳与业务隔离，基于阿里云但不绑定公共逻辑；将来可替换云和 PostgreSQL/Mongo 适配器。
- Git 唯一源码，发布从 Git 经 CI 单向构建/发布；允许 OSS 存产物，禁止只在 OSS/控制台存在自研源码。
- 小阶段、多 PR、持续记录接续；已授权范围内自主推进，不在每阶段重新问是否开始。

## 当前阻塞与独立工作

- 先前交接的本机 Infisical session 阻塞已通过本次实际读取解除；仍须实测新 workflow 的 Actions OIDC 和资源权限，不用本机可读取替代 Actions 证明。
- 现有 Mongo 的跨云访问、最小权限和真实 FC 冷启动未验；不擅自开放公网/改防火墙/搬库。
- 本机 Docker daemon 不可用；CI 隔离 Mongo 已验证，不能因此声称生产网络可达。
- Mongo 写库/投递间隙尚未解决；S01/S02/S05 可以先推进，不绕过恢复要求。

## 接续步骤

1. `git fetch origin`，查询 #148 的 head、merge、CI 与 `git status --short --branch`。本分支已包含完整 S02 实现，不再把交接 `7facb75a` 当可合并版本。
2. 若 #148 未合并，检查最新 head 的所有必要 CI；有失败按真实日志修复，全部通过后再合并。API/浏览器 E2E 按 PR 条件 skipped，必须明确写出，不冒充跑通。
3. S02 工具用法见 `serverless/README.md`；正式构建保持 Linux x86_64 / CPython 3.12.12 / uv 0.12.2 的门禁。来源校验需要受控 CI 的可信 commit/ZIP SHA，manifest 不是签名。产物都在系统任务临时目录清理，S02 不保留或上传发布包。
4. 合并后进入 S03：独立分支、先读设计/plan/official-guidance，核验现有 Actions OIDC 权限与 FC/OSS 隔离方案；由 Git→CI 单向发布，不借本机 session 失败假定 OIDC 不可用。Mongo 访问不擅自开放公网或搬库。
5. S03 才接受控产物保留、OSS 与独立 FC 发布/回退；S04 再实测 FC/现有 Mongo 网络和冷热请求。S02 专项结果不能替代这些验收。

## 已查依据与未确认边界

- 阿里云 Python 运行时：https://help.aliyun.com/zh/functioncompute/python3/ 。查阅时 Python 3.12 为公测、Debian 11 x86_64；具体地域与实际 FC 配置仍需核验。
- Python 代码包：https://help.aliyun.com/zh/functioncompute/deploy-a-code-package-1 ；第三方依赖：https://help.aliyun.com/zh/functioncompute/install-third-party-dependencies-for-a-function 。依赖需与目标 Linux/架构匹配，不能上传 Windows wheel。
- uv 导出：https://docs.astral.sh/uv/concepts/projects/export/ 。候选 wheel target 为 `x86_64-manylinux_2_28`。
- 本机 Docker daemon 不可用；远程查询候选 `python:3.12.12-slim-bullseye` 镜像返回 not found。因此未验证 Debian 11 容器；Linux runner 冒烟也不能替代 FC/Debian 11 实测。若需要容器，先确认真实存在且合适的镜像，不假称已通过。
- S03/S04 才核实云资源、网络/权限、真实冷/热与 Mongo 首连。没有部署或费用发生；manifest 是完整性记录，不是数字签名，后续发布必须从受控 CI 获得可信摘要。
- 回退点为 master 的 S01 merge `4338dfb9`；本分支无生产变更，放弃 S02 草稿不会影响现役服务。

## 检查点规则

- 稳定小改动完成时写本地 commit；准备 PR、长操作或中断前推送可接续分支，记录提交、PR、未提交改动及下一条操作。
- 记录命令、结果/CI 链接和必要摘要，明确 passed/skipped/pending/failed/未运行；不复制巨大日志或秘密。
- 半成品留在工作分支并标注，不把未跑测试的阶段标完成。CI 等待时不无理由重跑全套或不断提交状态刷新。
- 接续先 `git status --short`、fetch、查询 PR head/CI；未提交改动先确认归属，禁止 reset/覆盖后再调查。
- 阶段收尾写“实际成果、剩余项、回退点、下一阶段”；系统临时目录不是唯一工作记录。不承诺额度恢复后平台自动执行，恢复工作以此记录为准。
