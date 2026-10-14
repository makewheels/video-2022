# 需求：评测数据改为可读 JSON

## 背景

当前 Dataset 使用一行一个对象的 JSONL。该格式适合流式处理，但人工打开时难以展开、比较和审阅，不符合评测集需要由产品负责人直接检查的目标。

用户还指出真实会话可能中途询问天气等能力外任务。这属于场景覆盖缺口，不应与文件格式迁移混为已经解决的能力。

## 目标

1. 将当前 Dataset、来源登记和旧 seed 改为格式化 JSON 数组；
2. 默认 CLI、文档和测试全部指向 `.json`；
3. 加载器继续兼容历史 `.jsonl`；
4. 保持 case ID、内容、Langfuse 稳定 item ID 和已有 Run 不变；
5. 在 living design 中登记天气、目标切换等 `session-chaos` 覆盖范围。

## 非目标

- 本需求不宣称已经补齐混乱多轮场景；
- 不重建或删除已有 Langfuse Dataset Run；
- 不改变 grader 判分语义；
- 不对生产数据执行读写。

## 验收标准

- 人工打开 `.json` 可看到缩进数组和独立对象；
- Smoke 15、Regression 49、Multi-turn 5 条数量及 case ID 不变；
- `.json` 和历史 `.jsonl` 均能被加载和校验；
- 默认 `video_agent eval` 使用 `.json`；
- 全量测试及 `git diff --check` 通过。
