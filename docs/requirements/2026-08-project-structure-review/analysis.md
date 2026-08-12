# 项目文件结构审计分析

> 基线：`origin/master` / `0d782215980fc52af3b92d9371666cb637b31417`
> 事实源：`git ls-files`（共 936 个被跟踪文件）；所有引用关系用 `grep` 在基线上实测。

# 总体结论

1. **顶层边界合理，不建议任何目录级重组。** 十个顶层模块职责清晰、各有构建/CI/文档锚点，任务线索中"顶层多端 monorepo 边界总体可能合理"得到证实。为对称或命名统一而移动稳定模块只会制造链接与 CI 风险。
2. **文档信息架构的目标态已经存在并被文字定义**（`docs/design/README.md`、`docs/requirements/README.md`）：`requirements` 管目标与验收、`design` 管当前设计、`plans` 管历史计划、`归档` 默认只读。现存问题不是架构缺失，而是**存量文档的状态标记与索引同步没跟上演进速度**。
3. **没有发现真实的内容级重复。** 重点抽查的 `docs/plans/*-design.md` 与 `*-plan.md` 文档对是互补关系（设计=目标/架构，计划=步骤/约束，均标注完成状态与 PR 链接）；`ai-agent/docs/eval-plan.md` 与 `docs/requirements/2026-08-video-agent-evaluation/` 是"早期计划 vs 现行体系"的取代关系而非重复；`ai-agent/docs/eval-report.md` 是 9 行跳转 stub，不含重复正文。
4. **确认 1 个生成文件误入 Git**：`web/test-results/.last-run.json`；根 `.gitignore` 同时缺少 `.venv/`、`test-results/` 等稳定规则，属于真实缺口。
5. **确认 1 个安全项**：`web/test-chat-ui.mjs` 内嵌硬编码登录 token，是本报告唯一的"必须修"。
6. `ai-agent` 是演进最快、漂移最多的模块：README 停在 v0.1、两篇文档与两个模块已断线（orphan）、6 篇 docs 未标记历史状态、旧 eval seed 未归位、模块未被 CI 覆盖。这些问题集中、边界清晰，适合拆成小 PR 逐个处理。

# 当前结构地图

顶层 tracked 文件分布（`git ls-files | awk -F/ '{print $1}' | sort | uniq -c`）：

| 顶层 | 文件数 | 职责 | 关键锚点 |
|---|---|---|---|
| `server/` | 395 | Java 后端，Maven 多模块：`video`（385，核心服务）+ `youtube`（9，子模块）+ 父 `pom.xml` | `server/pom.xml` `<modules>`；CI `backend-tests` |
| `android/` | 113 | Kotlin + Compose 客户端 | CI `android-tests` |
| `web/` | 73 | React SPA 用户前台（含 `src/`、`tests/`、`e2e/`） | CI `frontend-tests`（Vitest） |
| `ai-agent/` | 57 | 自然语言视频助手原型 + eval harness | 无 CI 入口（见问题 13） |
| `.github/` | 52 | `workflows/`（3 个：ci/deploy/release）+ `skills/`（49 个 agent 工具文件） | — |
| `cli/` | 49 | Python CLI：`video_cli/`（23）+ `tests/`（21） | CI `cli-tests` |
| `ios/` | 47 | SwiftUI 客户端（`VideoApp` 39 + `VideoAppTests` 7 + `project.yml`） | CI `ios-tests` |
| `docs/` | 79 | 见下方文档地图 | — |
| `test/` | 33 | 跨系统 E2E：`api/`（11）+ `browser/`（12）+ `cli/`（3）+ conftest/配置 | CI `api-e2e-tests`、`browser-e2e-tests` |
| `console/` | 28 | React 开发者门户 | deploy.yml 构建部署 |
| `scripts/` | 1 | `deploy.sh`（被 `.github/workflows/deploy.yml` scp 引用） | — |
| 根级文件 | 9 | `README.md`、`AGENT.md`、`CONTRIBUTING.md`、`Dockerfile`、`.gitignore`、`.dockerignore`、`.env.example`、`.readthedocs.yaml`、`llms.txt` | — |

`docs/` 内部（79 个文件）：

| 位置 | 文件数 | 定位 | 现状 |
|---|---|---|---|
| `docs/api/` | 10 | 接口契约（1-用户 至 10-点赞） | 活跃 |
| `docs/业务/` | 13 | 业务域详细设计（1-上传 至 13-互动） | 活跃 |
| `docs/测试/` | 11 | 测试体系文档（含 README） | 活跃 |
| `docs/归档/` | 6 | 历史资料，默认只读 | 符合约定 |
| `docs/plans/` | 25 | 2026-03 期间的历史设计/计划平铺文档 | 历史，已有渐进迁移约定 |
| `docs/requirements/` | 9 | 索引 README + 2 个需求目录（video-agent 评测、评测 JSON 迁移） | 活跃 |
| `docs/design/` | 2 | 当前设计（README + video-agent-evaluation） | 活跃（已存在于基线） |
| `docs/` 根级 | 3 | `1-关键设计.md`（导航中心）、`功能分析报告.md`、`CHANGELOG.md` | 活跃 |

`ai-agent/` 内部（57 个文件）：

| 位置 | 文件数 | 内容 |
|---|---|---|
| `video_agent/` | 22 | 根包 22 个模块共 5602 行；其中 `eval_*` 6 个模块 1556 行（28%）；`client.py`（952）与 `tools.py`（742）最大 |
| `tests/` | 13 | pytest，离线 fixture 为主 |
| `evals/` | 8 | README + `datasets/`（3 个版本化 Dataset）+ `judges/` + `schema/` + `sources/` + 旧 seed `video_agent_eval.json` |
| `docs/` | 6 | eval-plan、eval-report、implementation、agent-product-plan、optimization-plan、OPTIMIZED |
| 根级 | 21 | README、ANTHROPIC_SDK.md、pyproject.toml、uv.lock、.python-version、.env.example、.gitignore、fixtures/videos.json |

# 保持不变

以下边界经核对职责清晰、引用稳定，**不应移动、重命名或合并**：

- `server/video` 与 `server/youtube`：Maven 父子模块关系（`server/pom.xml` 显式声明），385 vs 9 的体量差反映真实职责差，不是失衡。
- `web/`、`console/`：两个独立前端应用，各有 `pnpm-lock.yaml` 与构建链路，无合并收益。
- `android/`、`ios/`：标准原生工程，CI 直连。
- `cli/`：`video_cli` 包 + `tests/`，uv 管理，被 `ai-agent` 作为工具面复用。
- `test/` 与各模块自有 `tests/`：前者是跨系统 E2E（需要真实后端），后者是模块单测，分层正确，不应合并。
- `scripts/`：仅 `deploy.sh`，被 deploy.yml 引用；单文件目录无需调整。
- `docs/api/`、`docs/业务/`、`docs/测试/`、`docs/归档/`：中文目录名被 README、`1-关键设计.md`、AGENT.md 大量内链引用，重命名只有美观收益，会破坏链接，不动。
- `docs/plans/`：历史平铺文档，`docs/requirements/README.md` 已明确"新需求不再新增、旧文档渐进迁移、不做一次性无审阅搬迁"，维持该约定。
- `.github/skills/`：有意提交的 agent 工具集，非误跟踪。
- `evals/datasets`、`evals/judges`、`evals/schema`、`evals/sources`：与 `evals/README.md` 和 `docs/design/video-agent-evaluation.md` 定义的数据分层一致，布局合理。
- `docs/1-关键设计.md` 与 `README.md` 的双入口定位：一个是文档地图中心、一个是项目门面，架构表存在部分重叠属可接受冗余（漂移部分计入问题 11，不做归并）。

# 发现的问题

严重度定义：**必须修**（安全或正确性风险）、**建议修**（真实问题，按后续 PR 处理）、**暂不处理**（仅美观或已有治理约定）。

| 严重度 | 当前路径 | 具体问题 | 仓库证据 | 建议 | 风险 |
|---|---|---|---|---|---|
| 必须修 | `web/test-chat-ui.mjs` | 一次性手动调试脚本，内嵌硬编码登录 token（`localStorage.setItem('token', …)` 形式，取值不在本报告引用）；疑似真实凭证进入 Git | `git ls-files` 确认被跟踪；全仓 `grep -rn "test-chat-ui" --include="*.json" --include="*.yml" --include="*.md"` 无任何引用；`web/package.json` scripts（dev/build/lint/preview）不引用 | 按凭证泄露处理：owner 在服务端作废该 token；后续 PR 删除脚本，或改为环境变量驱动并归入 `web/e2e/` | 低：无任何引用；删除不影响构建与 CI |
| 建议修 | `web/test-results/.last-run.json` | Playwright 运行状态文件（内容 `{"status": "failed", "failedTests": []}`）误入 Git | `git ls-files` 确认跟踪；`git log --follow` 显示由 `1fee44ee`（feat: add Anthropic Agent SDK…）引入；`.github/workflows`、`web/e2e/playwright.config.ts`、`web/package.json` 均无引用 | 后续 PR `git rm --cached` 下网，并在 `.gitignore` 补 `test-results/` | 无 |
| 建议修 | `.gitignore` | 根忽略文件缺少稳定的生成目录规则：无 `.venv/`（`test/`、`cli/` 均为 uv 项目，默认生成 `.venv`，当前仅 `ai-agent/.gitignore` 覆盖）；无 `test-results/`、`playwright-report/` | `git check-ignore` 实测：`ai-agent/.venv/x` 命中 ai-agent/.gitignore:4，`test/.venv/x`、`cli/.venv/x`、`web/test-results/x.json` 均不命中；根 .gitignore 仅有旧式 `test/venv/` | 根 `.gitignore` Python/Node 节补 `.venv/`、`test-results/`、`playwright-report/` | 无：只影响未跟踪文件 |
| 建议修 | `.readthedocs.yaml` | 声明 MkDocs 构建（version 2 + python 3.12），但仓内不存在 `mkdocs.yml`，为死配置 | `git ls-files \| grep -i mkdocs` 为空 | 删除该文件；若 owner 确要 RTD 站点则反向补齐 mkdocs 配置 | 删除无仓内影响；若 RTD 平台绑定过站点需先解绑（见不确定项） |
| 建议修 | `server/video/Dockerfile-video`、`server/video/deploy-video.sh` | 遗留部署文件；现行链路为根 `Dockerfile` + `scripts/deploy.sh` + `.github/workflows/deploy.yml` | 全仓 `grep -rn "Dockerfile-video\|deploy-video"`（.yml/.md/.sh/核心文档）无引用 | owner 确认服务器无手工引用后删除 | 极低；删除前需一次人工确认 |
| 建议修 | `ai-agent/README.md` | 目录树停在 v0.1：`video_agent/` 只列 6 个模块（实际 22）、`docs/` 只列 2 篇（实际 6）、`evals/` 只列旧 seed（实际 4 个子目录 + README）、未列 `tests/`、`pyproject.toml`、`uv.lock`；安装命令 `pip install -r requirements.txt` 引用不存在文件 | `git ls-files ai-agent` 中无 `requirements.txt`（实为 pyproject.toml + uv.lock）；`wc -l ai-agent/video_agent/*.py` 为 22 个模块 | 按现状重写 README（目录树、uv 安装、eval 命令入口） | 无 |
| 建议修 | `ai-agent/ANTHROPIC_SDK.md`、`ai-agent/video_agent/server_anthropic.py`、`ai-agent/video_agent/anthropic_client.py` | 文档描述 `serve --use-anthropic`，但 `__main__.py` 只接线 `--optimized`（→ `server_optimized.py`）与默认（→ `server.py`）；两个模块在 `.py` 全仓 grep 无任何 import，为 orphan；文档位于 ai-agent 根而非 `docs/` | `grep -rn "server_anthropic\|use_anthropic" --include="*.py" ai-agent` 为空；`grep -rln "anthropic_client" --include="*.py" ai-agent` 仅命中 server_anthropic.py 自身；`__main__.py:91,251-259` 仅两个 serve 分支 | owner 二选一：恢复接线（修文档）或移除代码+文档；若保留文档，迁入 `ai-agent/docs/` | 移除 orphan 代码无运行时影响；方向决策属 owner |
| 建议修 | `ai-agent/docs/`（6 篇） | 历史文档未标记状态，与现行体系并存易被误读为当前事实：`eval-plan.md`（2026-04 首版 eval 计划，其"推荐扩展"已由 2026-08 评测体系落地）、`implementation.md`（v0.2.0 / 38 工具时代快照）、`agent-product-plan.md`（2026-04，短中期项多已完成）、`optimization-plan.md` + `OPTIMIZED.md`（优化计划/结果对）、`eval-report.md`（9 行跳转 stub） | 各文档正文时间戳与功能盘点；现行体系事实源为 `docs/requirements/2026-08-video-agent-evaluation/` 与 `docs/design/video-agent-evaluation.md` | 加状态头或迁入 `ai-agent/docs/archive/`（git mv 保留历史）；`eval-report.md` 在入链检查后可删除 | 低；迁移前 grep 入链 |
| 建议修 | `ai-agent/evals/video_agent_eval.json` | 49 条旧 seed，已无任何代码加载，只剩文档追溯性引用；位置在 `evals/` 根，易被当作现行资产 | `.py` 全仓 grep `video_agent_eval` 无命中（仅 .md 引用）；`evals/README.md:39` 明确"49 条旧用例只是 seed…迁移后的 regression_v1 保留 legacy 字段追溯"；`design-and-plan.md:643` 本就计划"迁移完成后标记兼容或移除" | `git mv` 至 `ai-agent/evals/legacy/`，同步更新 `evals/README.md` 等 living docs；历史需求文档中的引用不改写 | 低；无代码加载，测试兜底验证 |
| 建议修 | `web/README.md` | 仍为 Vite 官方模板原文（React Compiler、ESLint 模板建议），无本项目任何信息；也未记录 `web/e2e` 的本地运行前提 | 文件正文即模板原文；`web/e2e/playwright.config.ts` 的 webServer 需 `python3 -m video_agent serve`（依赖 ai-agent 包可被 python 导入） | 重写为项目说明：技术栈、命令、`e2e/` 对 `ai-agent` `video_agent` 包的依赖前提 | 无 |
| 建议修 | `web/e2e/`（含 `playwright.config.ts`、`chat.spec.ts`） | 未接入 CI 且无 npm script；对 `ai-agent` 有未声明的跨模块运行依赖 | `web/package.json` scripts 无 e2e；`.github/workflows` 无 web/e2e 引用；`@playwright/test` 在 devDependencies；config 的 webServer 先起 `video_agent serve`（8765）再起 `npm run dev`（5173） | 在重写后的 web/README 明确"本地手动 + 依赖前提"；是否接 CI 由 owner 决定（需解决 python 环境与端口） | 接 CI 有环境成本；不接则保持文档说明 |
| 建议修 | `README.md`、`docs/1-关键设计.md`、`llms.txt`、`docs/requirements/README.md`、`docs/requirements/2026-08-video-agent-evaluation-json/README.md` | 索引漂移：README API 表 8 行（`docs/api` 实有 10 篇，缺 9-评论、10-点赞）；README 与 `1-关键设计.md` 业务表 10 行（`docs/业务` 实有 13 篇，缺 11-13）；两处需求状态仍为"PR #108 待合并"（基线 HEAD 即 #108 合并 commit）；`llms.txt` 未覆盖 `ai-agent` 模块 | `git ls-files docs/api` 10 篇、`docs/业务` 13 篇 vs README 表格行数；`git log` HEAD `0d782215` 即 `refactor: 评测集迁移为可读 JSON (#108)`；`grep -n "ai-agent" llms.txt` 无命中 | 后续 PR 统一同步索引与状态 | 无 |
| 建议修 | `ai-agent/video_agent/eval_*.py`（6 个模块） | eval 子系统在根包聚集：6 个模块 1556 行（占包 28%），内聚度高（`eval_runner`→dataset/graders/user_simulator；`eval_langfuse`→graders/judge/user_simulator；`__main__`→`eval_runner` + 惰性导入 `eval_dataset`/`eval_langfuse`），具备子包化条件 | `grep -n "from .eval_\|from video_agent.eval" --include="*.py"` 完整导入图；`wc -l` 体量统计 | 可迁 `video_agent/evaluation/` 子包（见 plan.md PR-4）；影响面：5 个测试文件的绝对导入、`__main__.py`、`evals/README.md` 与历史需求文档中的 `python -m video_agent.eval_dataset` 命令。**Langfuse 稳定 ID 不受影响**：`stable_item_id = uuid5(_ITEM_NAMESPACE, f"{dataset_name}:{case_id}")`（`eval_langfuse.py:36-38`），只依赖 Dataset 名与用例 id，与模块路径无关。CI 无引用。若 owner 认为收益不足，维持现状也可接受 | 中低：机械替换，`uv run pytest` 可兜底；历史文档中的旧命令不 retroactive 改写 |
| 建议修 | `ai-agent/`（整体） | 模块未被 CI 覆盖：`tests/` 13 个测试文件无 workflow 入口 | `grep -n "ai-agent" .github/workflows/*.yml` 无命中；CI 现有 9 个 job 不含 ai-agent | 增加独立 CI job（`uv run pytest`，离线 fixture 无外部依赖）；或由 owner 记录"有意排除"的决策 | 增加少量 CI 时长 |
| 暂不处理 | `docs/plans/`（25 篇） | 与 `docs/requirements`、`docs/design` 存在职责重叠观感；`*-design.md` 与 `*-plan.md` 成对出现 | 抽查 `2026-03-07-131609-e2e-testing-design.md` 与 `…-131610-…-plan.md`：前者为目标/架构、后者为步骤/约束，互补非重复，均标 ✅ 已完成 + PR 链接；`docs/requirements/README.md` 已定渐进迁移约定 | 维持现状与既定约定，不做一次性搬迁 | 搬迁反而会丢失历史上下文 |
| 暂不处理 | `docs/1-关键设计.md` 与 `README.md` 重叠小节 | 架构图/分包表部分重叠 | 双入口定位不同（导航中心 vs 项目门面） | 保留双入口，仅在问题 11 中同步漂移的表格 | 归并收益低 |
| 暂不处理 | `console/` 无测试体系 | 开发者门户无成体系单测 | `docs/功能分析报告.md`"仍需完善"表已记录该工程点 | 属测试覆盖问题而非结构问题，不在本审计处理 | — |
| 暂不处理 | `docs/业务/`、`docs/api/` 等中文目录名 | 命名风格问题 | 被 README、`1-关键设计.md`、AGENT.md 大量内链引用 | 重命名仅美观收益且破坏链接，不动 | — |

# 建议目标结构

顶层目录**完全不变**。仅 `ai-agent/` 内部与忽略规则有目标态调整（全部经后续 PR、复核后实施）：

```text
ai-agent/
├── README.md                  # 重写：现状目录树 + uv 安装 + eval 入口
├── docs/
│   ├── archive/               # eval-plan / implementation / agent-product-plan /
│   │                          # optimization-plan / OPTIMIZED（加状态头或迁入）
│   └── （ANTHROPIC_SDK.md 若保留则迁此；eval-report.md 入链检查后删除）
├── evals/
│   ├── README.md              # 同步 legacy 路径与命令
│   ├── datasets/  judges/  schema/  sources/   # 不变
│   └── legacy/
│       └── video_agent_eval.json   # 旧 seed 归位
├── video_agent/
│   ├── evaluation/            # 可选（PR-4）：eval_dataset / eval_graders / eval_judge /
│   │                          #   eval_langfuse / eval_runner / eval_user_simulator
│   └── …其余 16 个模块不变     # server_anthropic.py / anthropic_client.py 由 owner 决策去留
└── tests/                     # 可选（PR-4）：eval 相关 5 个测试文件的导入同步更新
```

```text
# 根 .gitignore 增补（PR-1）
.venv/
test-results/
playwright-report/
```

# 不确定项

- `server_anthropic.py` / `anthropic_client.py` 的去留：代码与文档均已断线，但是否代表已放弃的产品方向，需 owner 决策。
- `web/test-chat-ui.mjs` 中 token 是否仍然有效：按安全惯例不验证凭证有效性，直接按泄露处理（作废）最稳妥。
- `.readthedocs.yaml` 是否在 Read the Docs 平台绑定过实际站点：仓内无 mkdocs 配置可断定其为死配置，但平台侧绑定状态只有 owner 能查。
- `ai-agent` 未进 CI 是有意省略还是遗漏：测试为离线 fixture，接入成本低，倾向遗漏，但需 owner 确认。
- `docs/plans/` 渐进迁移的时间表：现有约定未给期限，本报告不增设。
- `web/e2e` 是否值得接入 CI：需权衡 python 环境准备与端口占用的 CI 成本。

# 最终建议

按 [plan.md](plan.md) 拆分的 4 个后续 PR 推进，顺序即优先级：

1. **PR-1 安全与生成文件清理**（必须修 + .gitignore 缺口 + 死配置/遗留文件确认删除）；
2. **PR-2 文档索引同步与 ai-agent 文档分层**（README/关键设计/llms.txt/需求状态同步，web 与 ai-agent README 重写，历史文档标记归档）；
3. **PR-3 evals legacy 归位**（`video_agent_eval.json` → `evals/legacy/`）；
4. **PR-4（可选）evaluation 子包化 + ai-agent CI job**。

全部后续 PR **不依赖** `improve/eval-json-format` 先合并：该分支与 `origin/master` 的内容 diff 为空（`git diff --stat origin/master origin/improve/eval-json-format` 无输出），其改动已以 PR #108 合入基线。

本审计 PR 本身只新增本目录三个文档，不触碰任何现有文件；`docs/requirements/README.md` 的索引登记与 `docs/CHANGELOG.md` 更新并入 PR-2 处理，以满足本 PR "只含三个新文件" 的验收约束。
