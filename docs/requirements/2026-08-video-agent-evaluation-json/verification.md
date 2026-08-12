# 验证记录

> 状态：本地验收通过，待 PR

## 迁移结果

- `video_agent_eval`：49 条旧 seed，`.jsonl` → 格式化 `.json`；
- `scenario_sources`：33 条来源登记，路径说明同步改为 `.json`；
- Smoke / Regression / Multi-turn：15 / 49 / 5 条，全部改为格式化 `.json`；
- 默认 `video_agent eval --suite ...` 路径已指向 `.json`；
- `load_eval_cases`、validator 和 runner 共用统一加载入口，旧 JSONL、空行和 `#` 注释行仍兼容。

## 语义对比

迁移前后按 `id` 排序、键排序后计算 SHA-256：旧 seed 和三套 Dataset 摘要逐项一致。来源登记只把两处失效的 `.jsonl` / `Git JSONL` 路径文字改为 `.json` / `Git JSON`，规范化该预期替换后摘要一致。

case ID 和 Dataset name 没有变化，因此 Langfuse 的稳定 item UUID 与已有 Run 不变。本次没有连接或写入 Langfuse。

## 验证命令

```bash
cd ai-agent
uv run python -m video_agent.eval_dataset validate evals/datasets/video_agent_smoke_v1.json
uv run python -m video_agent.eval_dataset validate evals/datasets/video_agent_regression_v1.json
uv run python -m video_agent.eval_dataset validate evals/datasets/video_agent_multi_turn_v1.json
uv run pytest -q
```

结果：三套数据分别 15 / 49 / 5 条通过；`178 passed, 34 warnings`。warnings 为既有 FastAPI/Starlette 和 `datetime.utcnow` 弃用提醒。

`jq empty`、CLI help 和 `git diff --check` 通过。

## 尚未包含

天气等能力外请求、目标切换、混合所有权、忽略回答和长会话只登记在当前设计的 `session-chaos-v1` 范围，本次格式迁移没有伪装成场景覆盖已经完成。
