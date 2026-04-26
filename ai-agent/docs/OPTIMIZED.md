# AI Agent 优化版本

## 新增功能

### 1. MongoDB 会话持久化
- 多轮对话历史存储
- 自动 TTL 过期（7天）
- 并发会话支持

### 2. 上下文管理
- 智能 token 估算
- 自动消息裁剪
- 历史对话摘要

### 3. 错误处理
- 重试机制（指数退避）
- 工具执行错误捕获
- API 错误分类处理

### 4. 性能优化
- 异步 MongoDB 操作
- 流式响应优化
- 并发请求支持

## 使用方法

### 启动优化版本

```bash
# 使用 --optimized 参数
python -m video_agent serve --optimized

# 或设置环境变量
export USE_OPTIMIZED=true
python -m video_agent serve
```

### 环境配置

在 `.env` 文件中添加：

```bash
# MongoDB 连接
MONGODB_URI=mongodb://localhost:27017

# 启用优化版本
USE_OPTIMIZED=true
```

### API 端点

#### 健康检查
```bash
GET /health
```

返回：
```json
{
  "status": "ok",
  "backend": "fixture",
  "model": "MiniMax-M2.7",
  "features": ["mongodb_sessions", "context_management", "error_handling"]
}
```

#### 流式聊天
```bash
POST /chat/stream
Content-Type: application/json

{
  "query": "搜索视频",
  "session_id": "user-123",
  "confirm_write": false
}
```

#### 删除会话
```bash
DELETE /sessions/{session_id}
```

#### 清理旧会话
```bash
POST /sessions/cleanup?days=7
```

## 架构说明

### 会话管理 (SessionManager)
- 基于 Motor (异步 MongoDB 驱动)
- 自动创建索引和 TTL
- 支持消息追加和批量更新

### 上下文管理 (ContextManager)
- Token 估算（中文/英文自适应）
- 消息裁剪策略：保留系统提示 + 最近消息
- 自动生成历史摘要

### 错误处理 (ErrorHandler)
- 分类错误：rate_limit, timeout, auth_error, tool_error
- 重试装饰器：`@retry_async`, `@retry_sync`
- 可重试错误自动识别

## 测试

```bash
# 运行所有测试
pytest ai-agent/tests/ -v

# 运行特定测试
pytest ai-agent/tests/test_session_manager.py -v
pytest ai-agent/tests/test_context_manager.py -v
pytest ai-agent/tests/test_error_handler.py -v
pytest ai-agent/tests/test_e2e_optimized.py -v
```

## 性能指标

- 会话查询: < 10ms (MongoDB 索引)
- 上下文裁剪: < 5ms (8000 tokens)
- 错误重试: 3次，指数退避 (1s, 2s, 4s)
- 并发会话: 支持 100+ 并发

## 监控建议

1. MongoDB 连接池监控
2. 会话数量和大小统计
3. API 错误率和重试次数
4. 响应时间分布

## 后续优化方向

1. 向量搜索集成 (MongoDB Atlas Search)
2. 缓存层 (Redis)
3. 流量限制和配额管理
4. 详细的性能指标收集
