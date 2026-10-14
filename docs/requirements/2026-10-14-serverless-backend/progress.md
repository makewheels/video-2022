# 接续记录

额度中断、上下文压缩或更换执行环境后，先读本页，再核对 Git、PR、CI。记录必须随阶段和重要状态更新，不能只在额度用完时补写；不保存密钥、用户数据或临时下载内容。

## 当前快照（2026-10-14，已恢复 S02，待 CI）

- S01 #147 已合并，回退点 `4338dfb9`；本机 fetch 后切到 `feat-serverless-release-provenance`，从交接 `7facb75a` 接续。
- S02 补齐 requirements 包内绑定、依赖 METADATA 对照、坏 ZIP 拒绝及有意义的回归测试；新增独立 Linux packaging job，构建真实 PR head 两次并隔离入口/native 冒烟，纳入汇总门禁。
- 本地 macOS CPython 3.12.12 / uv 0.11.7：Ruff 通过；77 passed、真实 Mongo 1 skipped、既有 TestClient warning 1；语法/空白与 500 行门禁通过。pyproject/lock-only 快照 export 实测通过。
- PR [#148](https://github.com/makewheels/video-2022/pull/148) 已创建；首轮 packaging 因直接 python 使用 runner 默认解释器失败，已改为 uv 显式选择 3.12.12，待新 head 检查。
- Linux 固定 CPython 3.12.12 / uv 0.12.2 的构建与 CI 仍待运行。当前提交用 `git log -1` 查询；PR/CI 从本分支查询。未通过前不合并、不宣称 S02 完成。
- 生产仍为 Java；未切流、未改 Mongo、未上传 OSS、未创建/发布 FC。
- 下一步：push、创建 S02 PR，观察 packaging 和全部必要 CI；失败按真实日志修复。通过后记录双包摘要/大小和 counts，再按既有授权合并。S03 才做隔离发布。

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

## S02 接续步骤（恢复工作后执行，本次暂停不执行）

1. `git fetch origin`，切到 `feat-serverless-release-provenance`，核对 `git status --short --branch`、`git log -1` 和远端；先读本页及 `docs/design/serverless-backend.md` 第 9 节。本次是 WIP 检查点，不要直接合并 master。
2. 审查四个新工具：正式构建限制 Linux x86_64 / CPython 3.12.12 / uv 0.12.2；Git 对象白名单取源码，handler/web 分包；锁文件导出带哈希 wheel 依赖；固定 ZIP 元数据；sidecar 记录来源；验证需外部可信 commit/ZIP SHA；临时解包验证两入口。当前只是实现草稿，尚无测试证明。
3. 优先补齐完整性缺口：requirements sidecar 目前只校验 manifest 自报摘要，考虑将 requirements 放入 ZIP，由可信 ZIP 摘要绑定；manifest 依赖版本未与包内 METADATA 对照；坏 ZIP 等异常应清晰拒绝。确认 uv export 对仅含 pyproject/lock 的快照能否工作，核对依赖过滤和原生扩展兼容。不要将未确认推断写成通过。
4. 在现有 `serverless/tests/` 增加有意义的构建/验证测试：干净提交、脏树拒绝、源码白名单、路径/符号链接/重复 ZIP 项、篡改和预期 SHA/commit、双包差异、固定 ZIP。测试、venv、缓存、构建产物都使用系统临时目录并清理，不生成项目内临时文件。
5. 接入独立 Linux CI packaging job，并加入 required summary 的 needs/失败判断。固定 Python/uv，在干净实际 PR head 上构建两次比较 ZIP；分离进程从解包产物验证 Handler/ASGI 和 BSON/Pydantic 原生模块。现有真实 Mongo job 保留，不以本地或 mock 替代云端验证。
6. 补工具使用说明、官方依据、验收记录、changelog；本地运行 Ruff/pytest。Windows 不绕过正式 Linux 构建门禁，不把不能在本地跑的构建说成通过。
7. 完成后再提一个 S02 PR，等待所有必要 CI 实际通过再按既有授权自行合并；PR 记录真实 counts/摘要/包体积和回退点。S03 才接隔离环境 Git → OSS → FC 发布和受控产物保留，本阶段不部署。

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
