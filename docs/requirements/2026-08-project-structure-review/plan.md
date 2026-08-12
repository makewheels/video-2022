# 实施记录

> 本文件记录结构重组的实际执行情况。审计发现与证据见 [analysis.md](analysis.md)。
> 全部改动在同一分支按主题拆为 6 个 commit，可逐条评审、逐条 revert。

## Commit 清单（基于 `origin/master` 0d782215）

| # | Commit | 内容 |
|---|--------|------|
| 1 | `docs: 审计项目文件与文档结构` | 新增本需求目录三个文档（README / analysis / plan） |
| 2 | `refactor: 清理误跟踪生成文件、死配置与遗留部署文件` | 删除 `web/test-chat-ui.mjs`（含硬编码 token）、`.readthedocs.yaml`（无 mkdocs.yml 的死配置）、`server/video/Dockerfile-video` 与 `deploy-video.sh`（零引用，云效时代遗留）；`web/test-results/.last-run.json` 退出跟踪；`.gitignore` 补 `.venv/`、`test-results/`、`playwright-report/` |
| 3 | `refactor: 归档历史计划文档，收编 ai-agent 文档与评测资产` | `docs/plans/`（25 篇）→ `docs/归档/plans/`；`ai-agent/docs/` 5 篇历史文档 → `ai-agent/docs/archive/`；删除 9 行跳转 stub `eval-report.md`（零入链）、`ANTHROPIC_SDK.md` 与 orphan 模块 `server_anthropic.py`、`anthropic_client.py`；`evals/video_agent_eval.json` → `evals/legacy/`；`pyproject.toml` 移除 `anthropic` 依赖并重生成 `uv.lock`；同步 `AGENT.md`、`docs/功能分析报告.md`、`docs/requirements/README.md`、`evals/README.md` 与 `.github/skills/` 4 个文件中的路径指引 |
| 4 | `refactor: eval 子系统迁入 video_agent.evaluation 子包` | 6 个 `eval_*` 模块迁入 `video_agent/evaluation/`；修正 `eval_runner.py` 对 `trace` 的跨包导入（`from .. import trace`）与 `eval_judge.py` 的 rubric 相对路径（`parents[1]`→`parents[2]`，由测试捕获）；同步 `__main__.py` 与 5 个测试文件导入、`evals/README.md` 命令 |
| 5 | `refactor: 移除已被 agent 循环取代的 planner/llm 残留模块` | 删除 v0.1 架构残留、无任何引用的 `planner.py`、`llm.py` |
| 6 | `test: 新增 ai-agent CI 测试 Job` | `ci.yml` 新增 `ai-agent-tests`（uv + pytest，离线 fixture），并加入 `build-and-test` 汇总门禁；CI 由 9 个 Job 变为 10 个 |

第 7 个 commit（`docs:` 索引同步）与本文件一并提交：README/关键设计补全 API 9-10 与业务 11-13、架构段落补 `console`/`ai-agent`，`llms.txt` 补 ai-agent，两处需求状态修正（#108 已合并），重写 `web/README.md`（原 Vite 模板）与 `ai-agent/README.md`（原 v0.1 目录树、`pip install -r requirements.txt` 指向不存在文件），CHANGELOG 登记，CI Job 数描述同步（README/CONTRIBUTING/AGENT）。

## 验证结果

| 验证项 | 命令 | 结果 |
|---|---|---|
| ai-agent 全量测试 | `cd ai-agent && uv run pytest tests/` | ✅ 178 passed（子包化、orphan 删除后各跑一遍） |
| Dataset 校验（新模块路径） | `uv run python -m video_agent.evaluation.eval_dataset validate evals/datasets/*.json` | ✅ smoke 15 条通过 |
| eval CLI 冒烟 | `uv run python -m video_agent eval --suite smoke --trials 1` | ✅ 链路完整（case 加载→trial→grading 正常；仅 LLM 调用因本环境无凭证返回 401，与基线行为一致） |
| CI YAML | `yaml.safe_load` | ✅ 通过 |
| 引用残留 | 全仓 grep 移动/删除路径 | ✅ living 文档与代码无残留（历史文档与 CHANGELOG 记录有意不改写） |
| Langfuse 稳定 ID | 代码走查 `eval_langfuse.py` | ✅ `uuid5(dataset_name:case_id)` 与模块路径无关，迁移不影响 Dataset/Run |
| `git diff --check` | — | ✅ 通过 |

## 仍需人工处理（仓库外动作）

1. **作废泄露的 token**：`web/test-chat-ui.mjs` 已删除，但其中硬编码的登录 token 曾在 Git 历史出现，请 owner 在服务端作废。
2. **确认 Read the Docs 平台侧无站点绑定**（仓内 `.readthedocs.yaml` 已删，属死配置）。
3. **确认服务器无对 `deploy-video.sh` / `Dockerfile-video` 的手工引用**（现行链路为根 `Dockerfile` + `scripts/deploy.sh` + `deploy.yml`）。
4. 远端残留分支 `origin/improve/eval-json-format` 内容与 master 完全一致（diff 为空），是否删除由 owner 决定。

## 回滚

- 整体回滚：revert 本 PR 的 merge commit。
- 单项回滚：按上表逐条 revert 对应 commit；文件移动均用 `git mv`，历史连续。
- 注意顺序：commit 4（子包化）依赖 commit 3 之后的树状态，单独 revert 中间 commit 时需连同其后相关 commit 一起评估。

## 依赖说明

不依赖 `improve/eval-json-format` 先合并：该分支与 `origin/master` 内容 diff 为空，改动已随 PR #108 合入基线。
