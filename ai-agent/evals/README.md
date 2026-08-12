# Video Agent 评测数据

本目录中的格式化 Git JSON 数组是评测用例的版本化事实源；Langfuse Dataset 是执行、展示和跨版本比较的镜像，不反向覆盖 Git。加载器仍兼容历史 JSONL，但新资产统一使用便于人工审阅的 `.json`。

## 用例如何进入正式数据集

候选场景可以来自产品目标、代码契约、专业假设、生产失败、外部证据或合成对照。YouTube、哔哩哔哩等成熟平台资料只用于发现盲区，不能直接定义 video-2022 的正确行为。每条正式用例必须同时满足：

1. 与当前产品目标或明确 roadmap 相关；
2. 预期结果能够被工具轨迹、状态或可审阅 rubric 验证；
3. 正向实现与故意破坏的负向实现能够被区分；
4. 不把尚未实现的路径误写成当前必备能力；
5. 输入和期望稳定、可重复，且不依赖短期外部数据；
6. 与已有用例不重复，并能说明它保护的产品风险。

关键规则必须同时有正向与负向对照。外部链接可以不填，但 `rationale` 不能缺失。没有真实生产 bad case 时保留 `pending` 记录，禁止编造。

## 能力矩阵

| 能力簇 | 当前状态 | 主要证据 | 首版 Dataset |
|---|---|---|---|
| 视频数量、详情、最早/最近/最高排序 | supported | `ic-video-query-sort`、`ic-videos-json` | 是 |
| 标题解析、多候选消歧、参数来源 | partial | `pr-resolve-first`、`pr-multi-candidate-clarify`、`ic-resolve-shape` | 是；需补负向轨迹 |
| 转码状态、流量和统计 | partial | `ic-status-stats`、`ext-youtube-videos-api` | 是；外部维度仅作挑战 |
| 公共搜索和可见性隔离 | supported | `ic-search-visibility` | 是 |
| 评论读写 | supported | `ic-comment` | 是 |
| 播放列表读写、排序和恢复 | partial | `ic-playlist` | 只覆盖现有 seed；需补最终状态 |
| 通知查询和已读 | partial | `ic-notification` | 只覆盖查询；需补写状态 |
| 点赞、点踩和状态查询 | supported | `ic-like-dislike` | 是 |
| 观看历史和设备进度 | partial | `ic-watch-progress-client-id` | 历史进入；设备进度待补 |
| 个人资料、频道和订阅 | partial | `ic-profile-channel` | 仅个人信息 seed |
| 上传和 YouTube 转存 | partial | `ic-upload-transfer` | 未确认保护进入；成功状态待补 |
| 所有写操作确认 | supported | `pr-write-confirm`、`ic-write-guard` | 是，P0 veto |
| 工具错误真实性和恢复 | partial | `eh-error-recovery` | 待错误注入用例 |
| 多轮确认、取消、改口和指代 | partial | `eh-multi-turn-confirm-cancel` | 待多轮集 |
| trial 状态隔离和最终状态 | partial | `ic-fixture-backend`、`eh-state-isolation` | 待可变 fixture |
| 投稿合规与版权检查 | unsupported / roadmap | `eh-compliance-copyright`、`ext-bilibili-convention` | 否 |

`evals/legacy/video_agent_eval.json` 的 49 条旧用例只是 seed，不等于完整产品评测。迁移后的 `video_agent_regression_v1.json` 保留 `legacy` 字段用于逐条追溯；Smoke 是其中覆盖基础读取和 P0 写保护的子集。

## 文件

- `schema/eval_case.schema.json`：机器可读数据契约；
- `sources/scenario_sources.json`：来源、假设和准入状态；
- `datasets/video_agent_smoke_v1.json`：快速阻断集；
- `datasets/video_agent_regression_v1.json`：完整 seed 回归集；
- `datasets/video_agent_multi_turn_v1.json`：高风险确认、取消和消歧 Pilot；
- `legacy/video_agent_eval.json`：49 条旧 seed，仅用于追溯，不再被代码加载。

## 命令

```bash
uv run python -m video_agent.evaluation.eval_dataset validate evals/datasets/video_agent_smoke_v1.json
uv run python -m video_agent.evaluation.eval_dataset validate evals/datasets/video_agent_regression_v1.json
uv run python -m video_agent.evaluation.eval_dataset validate evals/datasets/video_agent_multi_turn_v1.json

# Langfuse 同步和实验命令由 eval CLI 提供；执行前通过环境变量注入凭证。
uv run python -m video_agent eval --suite smoke --trials 1
```
