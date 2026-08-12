# 后续实施计划

> 前提：本计划须经人工复核 [analysis.md](analysis.md) 后执行；每个 PR 独立评审、独立回滚。
> 通用约束：所有 PR 基于最新 `origin/master`；commit 格式 `type: 描述`；禁止直接 push master。

## 依赖总说明

所有后续 PR **均不依赖 `improve/eval-json-format` 先合并**。证据：`git diff --stat origin/master origin/improve/eval-json-format` 输出为空，该分支改动已以 PR #108 合入基线（`0d782215`）。远端残留分支的治理由 owner 自行决定，不在本计划内。

---

## PR-1：安全与生成文件清理

**目标**

消除硬编码凭证风险；让生成文件退出 Git；补齐 `.gitignore` 稳定规则；移除确认无引用的死配置与遗留部署文件。

**精确文件范围**

- 删除 `web/test-chat-ui.mjs`（owner 若想保留脚本，改为读取环境变量 token 并移至 `web/e2e/`，二选一在评审时定）
- `git rm --cached web/test-results/.last-run.json`（退出跟踪，本地文件不删）
- 修改 `.gitignore`：增补 `.venv/`、`test-results/`、`playwright-report/`
- 删除 `.readthedocs.yaml`（owner 确认 RTD 平台无站点绑定后执行）
- 删除 `server/video/Dockerfile-video`、`server/video/deploy-video.sh`（owner 确认服务器无手工引用后执行）

**需要更新的 import、链接、CI 或配置**

- 无 Python/TS import 影响（全部为无引用文件，analysis.md 已给出 grep 证据）。
- 无 CI 变更；`.github/workflows/deploy.yml` 使用根 `Dockerfile` 与 `scripts/deploy.sh`，不受遗留文件删除影响。
- 无 Markdown 链接影响（无文档引用这些文件）。
- **前置人工动作（非代码）**：owner 在服务端作废 `web/test-chat-ui.mjs` 中的 token。

**验证命令**

```bash
git ls-files | grep -E "test-results|last-run|test-chat-ui|readthedocs|Dockerfile-video|deploy-video"   # 应为空
git check-ignore web/test-results/x.json test/.venv/x cli/.venv/x                                      # 应全部命中
git diff --check
# 构建冒烟（确认 Dockerfile 链路不受影响）
docker build -t video-2022:pr1-check .
```

**回滚方法**

`git revert` 该 PR 的 merge commit 即可完整恢复所有被删文件与 ignore 规则（删除类操作由 Git 历史保证）；`.last-run.json` 若需重新跟踪，`git add -f` 恢复。

**是否依赖 `improve/eval-json-format` 先合并**：否。

---

## PR-2：文档索引同步与 ai-agent 文档分层

**目标**

让索引类文档与仓库现状对齐；重写两个失真 README；给 `ai-agent/docs/` 的历史文档标记状态或归档；登记本需求索引。

**精确文件范围**

- `README.md`：API 表补 9-评论、10-点赞；业务表补 11-视频删除与级联、12-评论与回复系统、13-视频互动
- `docs/1-关键设计.md`：业务文档表同步补 11-13
- `llms.txt`：补 `ai-agent` 模块条目
- `docs/requirements/README.md`：`评测数据改为可读 JSON` 状态改为已合并（PR #108），并登记本需求目录
- `docs/requirements/2026-08-video-agent-evaluation-json/README.md`：状态头同步为已合并
- `docs/CHANGELOG.md`：补登记本审计 PR 与 PR-1 的时间线条目
- `web/README.md`：重写为项目说明（技术栈、命令、`e2e/` 需本地可导入 `video_agent` 包的前提说明）
- `ai-agent/README.md`：按现状重写（目录树 22 模块/13 测试/6 docs/evals 分层；安装改为 `uv sync`；eval 命令以 `evals/README.md` 为准）
- `ai-agent/docs/eval-plan.md`、`implementation.md`、`agent-product-plan.md`、`optimization-plan.md`、`OPTIMIZED.md`：加历史状态头，或 `git mv` 至 `ai-agent/docs/archive/`
- `ai-agent/docs/eval-report.md`：grep 入链后删除（其跳转目标即现行体系文档）
- `ai-agent/ANTHROPIC_SDK.md`：随 owner 对 orphan 模块的决策——保留则迁 `ai-agent/docs/` 并修正不存在的 `--use-anthropic` 描述；不保留则与代码一同删除（代码删除见 PR-4 或随本 PR 由 owner 指定）

**需要更新的 import、链接、CI 或配置**

- 若 `ai-agent/docs/` 文件迁入 `archive/`：先 `grep -rn "docs/eval-plan\|docs/implementation\|docs/agent-product-plan\|docs/optimization-plan\|docs/OPTIMIZED\|docs/eval-report" --include="*.md"` 找入链并同步（已知 `ai-agent/README.md` 旧树引用 eval-plan/implementation，重写后自然消解；`eval-report.md` 被谁引用需实测）。
- `docs/design/README.md`、`docs/requirements/README.md` 中对 ai-agent 文档的引用按需更新。
- 无 CI、无代码 import 影响。

**验证命令**

```bash
git diff --check
# 相对链接人工检查：逐一打开改动文档中的新增/修改链接
# 死链扫描（改动的 ai-agent 文档）
grep -rn "eval-report\|ANTHROPIC_SDK" --include="*.md" ai-agent docs
```

**回滚方法**

`git revert` 该 PR 的 merge commit；`git mv` 过的文件随 revert 自动回移。

**是否依赖 `improve/eval-json-format` 先合并**：否。

---

## PR-3：evals 旧 seed 归位

**目标**

把已无代码加载的旧 seed 移出版本化 Dataset 的同一命名空间，消除"现行资产"歧义。

**精确文件范围**

- `git mv ai-agent/evals/video_agent_eval.json ai-agent/evals/legacy/video_agent_eval.json`
- 修改 `ai-agent/evals/README.md`：更新第 39 行附近对旧 seed 的路径引用
- 修改 `docs/design/video-agent-evaluation.md`：如引用旧路径则同步（评审时 grep 确认）

**需要更新的 import、链接、CI 或配置**

- 无代码 import：`.py` 全仓 grep 已确认无加载（analysis.md 问题 9）。
- 历史需求文档（`docs/requirements/2026-08-video-agent-evaluation/design-and-plan.md`、`…-json/verification.md`）中的旧路径引用**不改写**——它们是历史记录，保持当时事实。
- 无 CI 影响。

**验证命令**

```bash
cd ai-agent && uv run pytest tests/ -v                                  # 全量测试兜底
grep -rn "evals/video_agent_eval.json" --include="*.py" .               # 应仍为空
grep -rn "evals/video_agent_eval.json" --include="*.md" ai-agent docs/design   # living docs 应只剩 legacy/ 新路径
git diff --check
```

**回滚方法**

`git revert` 该 PR；或反向 `git mv` 回移单文件。

**是否依赖 `improve/eval-json-format` 先合并**：否。

---

## PR-4（可选）：`video_agent.evaluation` 子包化 + ai-agent CI 覆盖

**目标**

将高内聚的 eval 子系统迁出根包命名空间；补上 ai-agent 的 CI 入口。两项可同 PR 也可再拆，评审时定。若 owner 认为子包化收益不足，本 PR 可整体放弃，仅保留 CI job 部分。

**精确文件范围**

- `git mv` 以下 6 个模块至 `video_agent/evaluation/`（含新建 `__init__.py`）：
  `eval_dataset.py`、`eval_graders.py`、`eval_judge.py`、`eval_langfuse.py`、`eval_runner.py`、`eval_user_simulator.py`
- 同步修改 `ai-agent/tests/` 中 5 个测试文件的绝对导入：`test_eval_dataset.py`、`test_eval_graders.py`、`test_eval_judge.py`、`test_eval_langfuse.py`、`test_eval_user_simulator.py`
- 同步修改 `video_agent/__main__.py`：`from .eval_runner import run_eval_suite` 及函数内惰性导入（`eval_dataset`、`eval_langfuse`）
- 修改 `ai-agent/evals/README.md`：`python -m video_agent.eval_dataset validate …` 命令改为 `python -m video_agent.evaluation.eval_dataset validate …`
- 新增 `.github/workflows/` 中 ai-agent job（或独立 workflow）：`uv sync` + `uv run pytest`（离线 fixture，无外部凭证）
- 若 PR-2 中 owner 选择移除 orphan 模块，`server_anthropic.py`、`anthropic_client.py` 的删除可并入本 PR

**需要更新的 import、链接、CI 或配置**

- 包内相对导入随 `git mv` 保持（6 个模块互相引用均为相对导入，迁入同一子包后不变）。
- `video_agent/evaluation/__init__.py` 显式导出公共符号，保持 `__main__.py` 一处改动即可。
- 历史需求文档（`design-and-plan.md`、`results.md`、`verification.md`）中的旧模块路径命令**不改写**（历史记录）。
- **Langfuse 稳定 ID 不受影响**：`stable_item_id = uuid5(_ITEM_NAMESPACE, f"{dataset_name}:{case_id}")`（`eval_langfuse.py:36-38`），仅由 Dataset 名与用例 id 决定，与 Python 模块路径无关；Dataset 数据文件位置也不变。
- CI：新增 job 需计入 CONTRIBUTING.md 的 job 清单与"9 个 Job"描述（文档同步）。

**验证命令**

```bash
cd ai-agent
uv run pytest tests/ -v                                                   # 全量
uv run python -m video_agent eval --suite smoke --trials 1                # 离线 harness 冒烟
uv run python -m video_agent.evaluation.eval_dataset validate evals/datasets/video_agent_smoke_v1.json
uv run python -m video_agent.evaluation.eval_dataset validate evals/datasets/video_agent_regression_v1.json
uv run python -m video_agent.evaluation.eval_dataset validate evals/datasets/video_agent_multi_turn_v1.json
git diff --check
```

**回滚方法**

`git revert` 该 PR 的 merge commit；子包迁移为纯机械改动，revert 后导入路径即恢复原状。

**是否依赖 `improve/eval-json-format` 先合并**：否。与 PR-3 同触 `evals/README.md`，建议在 PR-3 合并后再开，避免 rebase 摩擦。
