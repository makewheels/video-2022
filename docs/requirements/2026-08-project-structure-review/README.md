# 项目文件结构审计

> 状态：审计完成，待人工复核
> 日期：2026-08-12
> 审计基线：`origin/master` / `0d782215980fc52af3b92d9371666cb637b31417`（`refactor: 评测集迁移为可读 JSON (#108)`）
> PR：待创建（创建后回填链接）

## 目标

审计 video-2022 的仓库目录与文档信息架构，区分合理边界、真实重复、生成文件误入 Git 和仅影响美观的问题，产出有证据、可复核、可拆成小 PR 执行的报告。

本需求 PR 只交付审计与迁移方案，不移动、重命名或删除任何现有文件。后续结构调整经复核后按独立 PR 实施。

## 文档

- [审计分析](analysis.md)：总体结论、当前结构地图、保持不变清单、发现的问题（含证据）、建议目标结构、不确定项、最终建议
- [后续实施计划](plan.md)：拆分为 4 个后续 PR，各含精确文件范围、需要同步的 import/链接/CI/配置、验证命令、回滚方法和依赖说明

## 结论摘要

- 顶层多端 monorepo 边界（`server` / `web` / `console` / `android` / `ios` / `cli` / `test` / `scripts` / `docs` / `ai-agent`）职责清晰、各有 CI 或文档锚点，**不需要目录级重组**。
- 文档分层目标态（`requirements` / `design` / `plans` / `归档`）已被 `docs/design/README.md` 与 `docs/requirements/README.md` 定义清楚；现存问题是存量文档的状态标记与索引同步没跟上，不是架构缺失。
- 发现 1 个安全项（`web/test-chat-ui.mjs` 内嵌硬编码登录 token），列为唯一的"必须修"。
- 发现 1 个确认误入 Git 的生成文件（`web/test-results/.last-run.json`），以及根 `.gitignore` 缺少 `.venv/`、`test-results/` 等规则。
- `ai-agent` 快速演进留下文档与代码漂移：README 目录树停在 v0.1 且引用不存在的 `requirements.txt`；`ANTHROPIC_SDK.md` 与 `server_anthropic.py`、`anthropic_client.py` 已断线；`docs/` 下 6 篇文档未标记历史状态；`evals/video_agent_eval.json` 旧 seed 已无代码加载。
- 任务线索中的"`docs/plans` 与 `docs/requirements` 内容重复"经抽查**否定**：design/plan 文档对是互补关系，且已有渐进迁移约定，建议保持现状。

## 范围与约束

- 事实源为 `git ls-files`（基线 commit 见上），本地忽略文件（`node_modules`、`.venv`、`__pycache__`、`.pytest_cache`）不作为仓库结构问题。
- 原始工作树仅用于只读核对（`git status --short`、分支与基线确认），全部审计与提交在基于 `origin/master` 的独立临时 worktree 完成。
- 未读取或输出任何 `.env`、API key 或 token 的具体取值；发现硬编码凭证时只记录位置与形态。
