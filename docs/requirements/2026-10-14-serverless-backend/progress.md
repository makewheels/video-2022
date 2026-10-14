# 接续记录

额度中断、上下文压缩或更换执行环境后，先读本页，再核对 Git、PR、CI。记录必须随阶段和重要状态更新，不能只在额度用完时补写；不保存密钥、用户数据或临时下载内容。

## 当前快照（2026-10-14，S02 专项验收通过，PR 收尾）

- S01 #147 已合并，回退点 `4338dfb9`；S02 从交接 `7facb75a` 恢复，工作分支 `feat-serverless-release-provenance`。
- S02 实现与专项验收通过：[PR #148](https://github.com/makewheels/video-2022/pull/148)。全仓库 CI/最终合并状态以 PR 当前 head 为准；恢复时必须查询，不根据这个历史快照重复实施或直接合并。
- [CI 38548901668](https://github.com/makewheels/video-2022/actions/runs/38548901668) 在代码提交 `22ddda59` 上：固定 Linux CPython 3.12.12 / uv 0.12.2 packaging success；Handler/Web 各两次 ZIP SHA 相同，来源验证与隔离入口/native 冒烟通过。原型测试 Ruff 通过、79 passed（含隔离 Mongo）、既有 warning 1。其他 CI 在本快照时仍收尾。
- Handler ZIP 1,385,323 bytes，SHA256 `d19761ade6bc6b6400370bc2699ab2216267763bd30f18f8e72c4d3af8da659a`；Web ZIP 4,748,028 bytes，SHA256 `3497049befa1cdd322ec76d50cc5c8977a1413d9d48ba870366b0fa855add5a3`。仅文档变更不会进入包；每次运行仍以当次 CI 来源记录为准。
- 本地 macOS CPython 3.12.12 / uv 0.11.7：完整测试 77 passed、真实 Mongo 1 skipped、warning 1；随后构建边界回归 35 passed；Ruff、500 行/空白门禁通过。Linux counts 包含新增路径检查，不能混用本地旧计数。
- 首轮 CI 用 runner 默认 Python 被严格 host gate 拒绝，已改为 uv 显式选择固定版本；旧 head CI cancelled，不用于最新提交验收。
- 生产仍为 Java；未切流、未改 Mongo、未上传 OSS、未创建/发布 FC。Linux runner 不等于 FC/Debian 11 云端验收。
- 下一步：查询 #148 和最新 CI，全部必要检查通过后按既有授权合并；随后从最新 master 为 S03 创建独立分支，先核验 Actions OIDC 权限、隔离资源/网络/发布回退设计。

## 用户约束（接续不能丢失）

- 全部现有业务、接口、用户操作及历史播放一致，不新增需求外业务字段或规则。
- 轻量 Python、无常驻保活、响应越快越好；没有批准具体毫秒阈值，不重复要求选框架/数值。
- 本轮继续现有 Mongo，TableStore 只属未来可能性；未授权迁库位置或采购固定资源。
- 外壳与业务隔离，基于阿里云但不绑定公共逻辑；将来可替换云和 PostgreSQL/Mongo 适配器。
- Git 唯一源码，发布从 Git 经 CI 单向构建/发布；允许 OSS 存产物，禁止只在 OSS/控制台存在自研源码。
- 小阶段、多 PR、持续记录接续；已授权范围内自主推进，不在每阶段重新问是否开始。

## 当前阻塞与独立工作

- 本机 Infisical CLI session 过期，已请求用户恢复；**不代表 Actions OIDC 不可用**。发布优先复用 Git 管理的 Actions 取密，核验身份权限，不因本机登录停止独立工作。
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
