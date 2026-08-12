# AI Agent 产品化改进计划

> 基于 2026-04-26 评估，记录从原型到产品的差距和改进路径。

## 当前状态

- 15 个工具、21 条 eval、单轮无状态
- 架构：heuristic/LLM planner → 工具调用（封装 CLI）→ 中文回答
- 无 agent loop、无多轮对话、无流式输出、无 HTTP 服务

## 改进路径

### 短期（原型加固到可用）

1. **多轮对话** — session 管理，记住上一轮上下文（video_id、关键词消歧结果）
2. **HTTP API 化** — FastAPI 包一层，让 Web/Console 可接入 chat UI
3. **接入真正的 Agent 框架** — 替换 intent routing 为 ReAct/Tool-use 循环，让模型自己决定调哪个工具、何时追问、何时确认
4. **补齐工具面** — CLI 全部命令暴露为 agent tool（video 删除/编辑、comment CRUD、playlist CRUD、notification 已读、like/dislike、YouTube 转存等）

### 中期（产品化）

5. **Web Chat UI** — 在现有 Web 前端加聊天面板
6. **流式输出** — 打字机效果
7. **用户认证打通** — agent 从 Web session 继承登录态
8. **Eval 扩展** — 场景补齐到 50+，加入真实后端 seed 脚本

### 长期

9. **多模态** — 视频帧抽取 + 视觉模型描述视频内容
10. **推荐/发现** —「有没有类似的视频？」
11. **自动化工作流** — 定时任务、条件触发

## 缺失场景

- 多轮对话（上下文继承）
- 复杂工作流（多步组合操作）
- 数据洞察/分析（排行榜、趋势、异常检测）
- 跨实体查询（播放列表内视频排序、评论最多视频等）
- 错误/边界（未登录、token 过期、网络超时）
- 主动建议
