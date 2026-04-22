# video-cli

Agent-friendly CLI tool for the video-2022 platform. Built with Python Click, following the [CLI-Anything](https://github.com/HKUDS/CLI-Anything) methodology.

## 安装

```bash
cd cli
pip install -e .
```

## 使用

所有命令默认输出 JSON，适合 AI Agent 和脚本集成。

### 全局选项

```bash
video-cli --base-url http://localhost:5022  # 指定 API 地址
video-cli --token <token>                   # 指定认证 token
video-cli --output table                    # 人类可读的表格输出
```

### 认证

```bash
# 请求验证码
video-cli auth login --phone 13800138000

# 提交验证码并登录（token 自动保存）
video-cli auth login --phone 13800138000 --code 111

# 查看当前用户
video-cli auth me

# 登出（仅清除已保存 token）
video-cli auth logout
```

### 本地配置

```bash
# 查看当前配置
video-cli config show

# 保存默认 API 地址
video-cli config set-base-url http://localhost:5022

# 手动保存 token
video-cli config set-token <token>

# 仅清除 token
video-cli config clear-token

# 清空整个本地配置文件
video-cli config clear
```

### 视频管理

```bash
# 列出我的视频
video-cli video list
video-cli video list --skip 0 --limit 10 --keyword "测试"

# 查看视频详情
video-cli video detail --id <videoId>

# 查看处理状态
video-cli video status --id <videoId>

# 更新视频信息
video-cli video update --id <videoId> --title "新标题" --visibility PUBLIC

# 删除视频
video-cli video delete --id <videoId>

# 预创建视频（用于上传，默认 USER_UPLOAD）
video-cli video create --file test.mp4
video-cli video create --file test.mp4 --type USER_UPLOAD

# 获取下载链接
video-cli video download-url --id <videoId>
```

### 评论

```bash
# 添加评论
video-cli comment add --video-id <id> --content "好视频！"

# 列出评论
video-cli comment list --video-id <id>

# 回复评论
video-cli comment add --video-id <id> --content "同意" --parent-id <commentId>

# 获取评论回复
video-cli comment replies --parent-id <commentId>

# 评论计数
video-cli comment count --video-id <id>

# 点赞评论
video-cli comment like --id <commentId>

# 删除评论
video-cli comment delete --id <commentId>
```

### 点赞

```bash
video-cli like like --video-id <id>
video-cli like dislike --video-id <id>
video-cli like status --video-id <id>
```

### 播放列表

```bash
# 创建播放列表
video-cli playlist create --title "我的收藏"

# 列出播放列表
video-cli playlist list

# 查看详情
video-cli playlist detail --id <playlistId>

# 添加/移除视频
video-cli playlist add-item --playlist-id <id> --video-id <videoId>
video-cli playlist delete-item --playlist-id <id> --video-id <videoId>

# 更新/删除/恢复
video-cli playlist update --id <id> --title "新名称"
video-cli playlist delete --id <id>
video-cli playlist recover --id <id>
```

### YouTube

```bash
# 查看 YouTube 视频信息
video-cli youtube info --youtube-id dQw4w9WgXcQ

# 转存到平台
video-cli youtube transfer --youtube-id dQw4w9WgXcQ

# 获取文件扩展名
video-cli youtube extension --youtube-id dQw4w9WgXcQ
```

### 统计

```bash
video-cli stats traffic --video-id <id>
video-cli stats aggregate --start 1704067200000 --end 1735689600000
```

### 观看

```bash
# 获取播放页信息
video-cli watch info --watch-id <id>

# 开始播放，会返回 playbackSessionId
video-cli watch start --watch-id <id> --video-id <videoId> --client-id <clientId> --session-id <sessionId>

# 上报播放心跳
video-cli watch heartbeat --playback-session-id <sessionId> --current-time-ms 30000 --is-playing

# 退出播放
video-cli watch exit --playback-session-id <sessionId> --current-time-ms 30000 --exit-type CLOSE_TAB

# 查询保存的播放进度
video-cli watch progress --video-id <videoId> --client-id <clientId>

# 历史记录
video-cli watch history --page 0 --page-size 20
video-cli watch clear-history
```

### 搜索

```bash
video-cli search 音乐
video-cli search 教程 --category 教育
video-cli search 游戏 --page 1 --page-size 10
```

### 通知

```bash
video-cli notification list --page 0 --page-size 20
video-cli notification read <notificationId>
video-cli notification read-all
video-cli notification unread-count
```

### 分享

```bash
video-cli share create --video-id <videoId>
video-cli share stats --short-code <shortCode>
```

## 环境变量

| 变量 | 说明 |
|------|------|
| `VIDEO_CLI_BASE_URL` | API 基础 URL |
| `VIDEO_CLI_TOKEN` | 认证 token |

## 配置文件

登录后 token 保存在 `~/.video-cli/config.json`。
