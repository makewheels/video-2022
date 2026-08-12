# Video Agent 评测与 Langfuse

> 状态：v1 已完成，生产回流待接入
> 开始日期：2026-08-12
> 基线：`master` / `9d37c1e2`

## 目标

建立 video-2022 AI Agent 的版本化评测体系：通过有效性验证后的 Dataset、确定性 grader、多轮场景、Langfuse Experiment 和线上 bad case 回流，判断任务结果、工具轨迹、安全、稳定性、质量、延迟与成本。

## 文档

- [设计与实施计划](design-and-plan.md)
- [验证与交付记录](verification.md)
- [Baseline 结果](results.md)

## 当前阶段

- [x] 建立需求文档和实施计划
- [x] 修正“外部资料优先”偏差：外部资料只用于发现盲区，用例由有效性决定
- [x] 确认腾讯云 Langfuse 接入
- [x] 建立 schema、来源/理由登记和版本化 Dataset
- [x] 完成确定性 grader、状态隔离和有限传输重试
- [x] 完成 Experiment、多 trial、多轮和可选 Judge 基础
- [x] 运行 Kimi K3 fixture baseline 并完成验收记录
- [ ] 接入脱敏生产 bad case、测试后端错误注入和人工 Judge 金标
