# 项目文件结构审计与重组

> 状态：审计与重组均已实施，待人工复核
> 日期：2026-08-12
> 审计基线：`origin/master` / `0d782215980fc52af3b92d9371666cb637b31417`（`refactor: 评测集迁移为可读 JSON (#108)`）
> PR：<https://github.com/makewheels/video-2022/pull/109>

## 目标

审计 video-2022 的仓库目录与文档信息架构，区分合理边界、真实重复、生成文件误入 Git 和仅影响美观的问题，并直接实施经证据支持的重组。

## 文档

- [审计分析](analysis.md)：总体结论、当前结构地图、保持不变清单、发现的问题（含证据）、建议目标结构、不确定项
- [实施记录](plan.md)：本 PR 实际执行的全部改动（按 commit 组织）、每步验证结果、仍需人工处理的事项

## 结论摘要

- 顶层多端 monorepo 边界（`server` / `web` / `console` / `android` / `ios` / `cli` / `test` / `scripts` / `docs` / `ai-agent`）职责清晰、各有 CI 或文档锚点，**不做目录级重组**（证据与成本分析见 analysis.md）。
- 实际实施的重组集中在文档信息架构与 `ai-agent` 内部：
  - `docs/plans/` 25 篇历史计划整体迁入 `docs/归档/plans/`，`docs/` 分层回归"现行 / 需求 / 设计 / 归档"四类；
  - `ai-agent/docs/` 历史文档迁入 `archive/`；删除已断线的 `ANTHROPIC_SDK.md` 与 4 个无任何引用的 orphan 模块（`server_anthropic.py`、`anthropic_client.py`、`planner.py`、`llm.py`），并移除 `anthropic` 依赖；
  - eval 子系统 6 个模块迁入 `video_agent/evaluation/` 子包，旧 seed 归位 `evals/legacy/`；
  - 生成文件与凭证清理：`web/test-results/.last-run.json` 退出跟踪，`.gitignore` 补 `.venv/`、`test-results/`、`playwright-report/`，删除内嵌硬编码 token 的 `web/test-chat-ui.mjs`、死配置 `.readthedocs.yaml`、遗留部署文件；
  - 索引同步：README、`docs/1-关键设计.md`、`llms.txt`、需求状态、`web/README.md` 与 `ai-agent/README.md` 重写；
  - 新增 ai-agent CI Job（CI 由 9 个 Job 变为 10 个）。
- 审计否定了一条预设线索：`docs/plans` 与 `docs/requirements` 并非内容重复，design/plan 文档对是互补关系；因此处理方式是归档而非合并去重。

## 验证

- `cd ai-agent && uv run pytest tests/`：178 通过（子包化与 orphan 删除后全绿）；
- `uv run python -m video_agent.evaluation.eval_dataset validate`：三个版本化 Dataset 校验通过；
- `git diff --check` 通过；移动/删除文件的全仓引用 grep 无残留（历史文档与 CHANGELOG 记录除外，有意不改写）；
- Langfuse 稳定 ID 不受影响：`stable_item_id = uuid5(dataset_name:case_id)`，与模块路径无关。

## 范围与约束

- 事实源为 `git ls-files`，本地忽略文件（`node_modules`、`.venv`、`__pycache__`、`.pytest_cache`）不作为仓库结构问题。
- 全部工作在基于 `origin/master` 的独立临时 worktree 完成，原始工作树未被触碰。
- 未读取或输出任何 `.env`、API key 或 token 的具体取值；`web/test-chat-ui.mjs` 中的 token 需 owner 在服务端作废（见 plan.md 遗留事项）。
