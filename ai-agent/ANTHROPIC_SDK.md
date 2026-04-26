# Anthropic Agent SDK 集成

## 新功能

✅ **多轮对话管理** - 支持上下文记忆，Agent 能记住之前的对话
✅ **Session 管理** - 每个用户独立的对话会话
✅ **更好的工具调用** - 使用 Anthropic 原生工具调用格式
✅ **流式返回** - 实时显示 Agent 思考和工具执行过程

## 配置

### 1. 安装依赖

```bash
cd ai-agent
pip install -r requirements.txt
```

### 2. 设置 API Key

编辑 `.env` 文件：

```bash
# 使用 Anthropic SDK
USE_ANTHROPIC_SDK=true
ANTHROPIC_API_KEY=sk-ant-xxxxx  # 你的 Anthropic API key
ANTHROPIC_MODEL=claude-3-5-sonnet-20241022

# 工具后端
VIDEO_AGENT_BACKEND=fixture  # 或 cli
```

### 3. 启动服务

```bash
# 使用 Anthropic SDK（推荐）
python3 -m video_agent serve --use-anthropic --port 8765 --backend fixture

# 或者通过环境变量
USE_ANTHROPIC_SDK=true python3 -m video_agent serve --port 8765 --backend fixture
```

## 多轮对话示例

```
用户: 我上传了几个视频？
Agent: 你一共上传了 8 个视频。

用户: 哪个播放量最高？  # ← Agent 记得之前提到的 8 个视频
Agent: 《黑神话：悟空 实况 #1》播放量最高，有 342 次观看。

用户: 它是什么时候上传的？  # ← Agent 知道"它"指的是哪个视频
Agent: 这个视频是在 2024-07-01 14:00:00 上传的。
```

## API 变化

### Session 管理

前端需要生成并传递 `session_id`：

```typescript
// 生成唯一 session ID
const sessionId = crypto.randomUUID();

// 发送请求时带上 session_id
fetch('/agent-api/chat/stream', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    query: '我上传了几个视频？',
    session_id: sessionId  // ← 必需
  })
});
```

### 清除会话

```bash
curl -X POST http://localhost:8765/session/clear?session_id=xxx
```

## 对比

| 特性 | 旧版 (MiniMax) | 新版 (Anthropic SDK) |
|------|---------------|---------------------|
| 多轮对话 | ❌ 不支持 | ✅ 支持 |
| Session 管理 | ❌ 无 | ✅ 有 |
| 上下文记忆 | ❌ 每次重新开始 | ✅ 记住历史 |
| 工具调用 | OpenAI 格式 | Anthropic 原生格式 |
| 响应速度 | ~5-6秒 | ~2-3秒 |

## 故障排查

### 1. API Key 未设置

```
⚠️  ANTHROPIC_API_KEY not set, using fallback mode
```

解决：在 `.env` 中设置正确的 `ANTHROPIC_API_KEY`

### 2. 服务启动失败

检查端口是否被占用：
```bash
lsof -i :8765
```

### 3. 多轮对话不工作

确保前端传递了 `session_id` 参数。

## 下一步

- [ ] 添加 Redis 持久化 session
- [ ] 支持导出/导入对话历史
- [ ] 添加对话摘要功能
- [ ] 集成向量数据库做语义搜索
