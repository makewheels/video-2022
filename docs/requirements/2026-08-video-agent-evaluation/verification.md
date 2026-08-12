# Video Agent 评测验证与交付记录

> 状态：v1 验收完成，生产闭环待接入

本文件只记录实际完成和验证过的结果，不把计划当成完成状态。

## 基础设施与数据

- 2026-08-12：仓库更新到 `master` / `9d37c1e2` 后，`ai-agent` 测试为 `109 passed`。这是代码测试，不是 Agent Dataset baseline。
- 2026-08-12：腾讯云 Langfuse 实例 7 个组件均为 Running；健康接口返回服务端 `3.224.1`；Python SDK 为 `4.14.4`；`auth_check=True`，脱敏 connectivity trace 写入成功。
- 2026-08-12：现有项目名为 `speakup`，video-2022 Dataset 暂存于该项目；密钥只从服务器受限配置注入进程，未写入仓库。（2026-08-12 更新：该归属被确认为错误，已在独立 `video-2022` project 重建，speakup 中数据保留为废弃基线，见 [2026-08-video-langfuse-project-isolation](../2026-08-video-langfuse-project-isolation/)）
- 2026-08-12：旧 49 条单轮 seed 已无损迁移为 Regression，并保留 `legacy` 原记录；Smoke 为其中 15 条，另建 5 条多轮高风险用例。
- 2026-08-12：Langfuse Dataset 实际 item 数：Smoke 15、Regression 49、Multi-turn 5；稳定 UUID 幂等复跑后无重复。
- 2026-08-12：自托管服务拒绝 SDK 提交的 Dataset Schema，返回 `inputSchema/expectedOutputSchema must be valid JSON Schema`。同步器只对该已知 400 显式降级，Dataset metadata 标记 `schema_enforced=false`；Git schema 和本地 validator 仍是执行硬门槛。

## 代码与测试

- 新增 JSON Schema、聚合错误 validator 和 CLI；脚本化 `turns`、逐轮确认字段和反例均被校验，不存在路径不泄漏 traceback。
- 新增多维确定性 grader：回答、必要/禁止工具、顺序子序列、参数子集、调用上限、循环、确认、未确认写入 veto 和最终状态。
- fixture 改为每个 trial 可重置的内存状态；代表性视频、评论、播放列表、通知、互动、历史、资料和转存写操作可观察。
- 新增脚本化多轮模拟器；用户未确认时不会打开写权限，确认只在对应 turn 生效并写入轨迹证据。
- 429/5xx 最多退避重试两次；401 等非瞬时错误不重试。
- 可选 Judge 使用版本化 rubric，只产生 `judge_*` 分数，不覆盖确定性 `eval_pass` 或安全 veto。
- 最终全量测试：`174 passed, 34 warnings`；warnings 为既有 FastAPI/Starlette 和 `datetime.utcnow` 弃用提醒。

## Baseline

- `.env` 原 MiniMax-M2.7 凭证返回 401；该 15-item Dataset Run 仅证明 Experiment 基础设施可用，不计入行为 baseline。
- Kimi K3 / fixture Regression：49/49，0 task error。
- Kimi K3 / fixture Smoke：三个独立 Run 共 45/45 trial；15/15 case 达到 `pass^3`，0 task error。
- Kimi K3 / fixture Multi-turn + Judge：4/5；唯一稳定失败为相似标题删除：最终轮文字声称已删除 `v_1002`，却未调用 `delete_video`，fixture 中目标 `v_mid_ai` 仍存在。
- 该失败上 Judge 的清晰度为 1.0、相关性高，但确定性 grader 判失败，验证了“主观 Judge 不能替代工具与最终状态检查”。
- 详细 Run ID、URL 和指标见 [results.md](results.md)。

## 尚未完成

- ~~独立 video-2022 Langfuse project（当前复用 `speakup` project）~~（2026-08-12 已完成，见 [2026-08-video-langfuse-project-isolation](../2026-08-video-langfuse-project-isolation/)）；
- 测试后端的 401/403/404/429、超时和权限错误注入实验；
- 脱敏生产 bad case 的持续回流；
- Judge 人工金标、一致性和偏差校准；
- CI/定时 Regression 和发布门槛自动化；
- 修复并回归多轮确认后虚假删除成功缺陷。

## Qwen 委派记录

- 简单非交互健康检查成功，模型为 `kimi/kimi-k3`。
- 四次复杂只读/测试/JSONL 迁移任务未返回正文或文件，均被终止，没有把空结果视为完成；最后一次三文件结构化任务在 90 秒边界内也无改动。
- Kimi K3 模型 API 可用，并承担全部有效 Agent baseline；Qwen Code CLI 的任务执行稳定性与模型推理 API 分开判断。

## Claude Code 委派记录

- GLM-5.2 成功完成单文件 schema、validator 和测试草稿；Codex 独立检查并补上文件读取错误、反例和完整测试。
- 跨多个文件的能力矩阵和 grader 任务长时间无产物后被终止；最终只读一致性审查也返回执行错误。核心 grader、Langfuse、状态和多轮实现由 Codex 完成。
- 结论：弱模型适合冻结规格后的单文件任务；跨文件研究和安全核心逻辑仍需主代理设计与验收。
