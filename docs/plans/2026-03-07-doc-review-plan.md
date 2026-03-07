> **状态:** 🔄 执行中

# 文档全面评审与改进 实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 全面评审并修复项目文档的断链、重复、缺失源码引用、缺少错误码、计划文档无状态标记等问题。

**Architecture:** 分 5 个 Task 推进：README 清理 → 业务文档增强 → API 文档补错误码 → Plans 标记状态 → 其它文档链接修复。每个 Task 独立可提交。

**Tech Stack:** Markdown, Mermaid

**关键约束:**
- **不要修改 `docs/归档/` 目录下的任何文件** — 那是人工时代的历史文档
- 所有变更走 PR，不直推 master
- 修改文档前先读原文件，理解上下文再改

---

### Task 1: README 清理 — 去重 + 修断链

**Files:**
- Modify: `README.md`

**Step 1: 删除 PR 表格，改为只链接 CHANGELOG**

README 中第 239-261 行的「开发记录」部分有完整 PR 表格，和 CHANGELOG.md 重复。改为：

```markdown
## 开发记录

详见 [CHANGELOG.md](docs/CHANGELOG.md)
```

**Step 2: 修复断链**

- 第 203 行 `[MongoDB 表结构](docs/2-MongoDB表结构.md)` → 改为 `[MongoDB 表结构](docs/归档/2-MongoDB表结构.md)`
- 第 209 行 `[部署指南](docs/4-部署.md)` → 改为 `[部署指南](docs/归档/4-部署.md)`
- 第 226 行 `[变更日志](docs/归档/6-变更日志.md)` → 改为 `[历史变更日志（2022-2025）](docs/归档/6-变更日志.md)`，并在旁边加链接到新的 CHANGELOG

**Step 3: 验证所有链接**

```bash
grep -n '\[.*\](docs/' README.md
```

逐一检查每个链接指向的文件是否存在。

**Step 4: 提交**

```bash
git add README.md
git commit -m "docs: README清理 — 去除PR表格重复、修复断链

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 2: 业务文档增强 — 添加源码位置引用

**Files:**
- Modify: `docs/业务/1-视频上传与去重.md`
- Modify: `docs/业务/2-视频转码.md`
- Modify: `docs/业务/3-视频播放.md`
- Modify: `docs/业务/4-视频存储.md`
- Modify: `docs/业务/5-YouTube下载.md`
- Modify: `docs/业务/6-用户与设备.md`
- Modify: `docs/业务/7-封面提取.md`
- Modify: `docs/业务/8-计费系统.md`
- Modify: `docs/业务/9-播放列表.md`
- Modify: `docs/业务/10-系统服务.md`

**Step 1: 为每篇业务文档添加「源码位置」章节**

在每篇文档末尾（或开头的概述区域）添加一个源码位置参考表，格式如下：

```markdown
## 源码位置

| 类 | 路径 | 说明 |
|----|------|------|
| VideoService | `video/src/main/java/.../video/VideoService.java` | 视频核心服务 |
| RawFileService | `video/src/main/java/.../file/RawFileService.java` | 原始文件处理 |
```

**每篇文档需要查找的关键类：**

1. **视频上传**: VideoCreateService, RawFileService, Md5Service, FileService, CheckService
2. **视频转码**: TranscodeService, AliyunMpsService, CloudFunctionTranscodeService, TranscodeCallbackService
3. **视频播放**: WatchService, PlayService, VideoReadyService, FileAccessService
4. **视频存储**: OssVideoService, OssService, OssLogService, OssInventoryService
5. **YouTube下载**: YoutubeService, YoutubeCallbackService, YoutubeVideoService
6. **用户与设备**: UserService, SessionService, ClientService, CheckTokenInterceptor
7. **封面提取**: CoverService, CoverCallbackService
8. **计费系统**: BillingService, WalletService, TransactionService, OssAccessFeeService, CreateBillTask
9. **播放列表**: PlaylistService, PlaylistItemService
10. **系统服务**: IdService, DingService, NotificationService, IpService, RedisService

**Step 2: 用 `find` 确认每个类的实际路径**

```bash
find video/src/main/java -name "*.java" | grep -i "VideoCreateService\|RawFileService\|Md5Service" | sort
```

对每篇文档执行类似命令，确保路径准确。

**Step 3: 检查文档中的技术描述是否准确**

对每篇文档中提到的关键逻辑，用 grep 在源码中搜索验证。例如：
- `docs/业务/2-视频转码.md` 提到轮询间隔 2 秒 → 搜索 `Thread.sleep` 或 `@Scheduled` 验证
- `docs/业务/8-计费系统.md` 提到凌晨执行 → 搜索 `@Scheduled` 的 cron 表达式验证

发现不一致时，以代码为准修正文档。

**Step 4: 提交**

```bash
git add docs/业务/
git commit -m "docs: 业务文档增强 — 添加源码位置引用、修正技术描述

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 3: API 文档补充错误响应码

**Files:**
- Modify: `docs/api/1-用户接口.md`
- Modify: `docs/api/2-上传视频接口.md`
- Modify: `docs/api/3-YouTube接口.md`
- Modify: `docs/api/4-播放视频接口.md`
- Modify: `docs/api/5-转码接口.md`
- Modify: `docs/api/6-播放列表接口.md`
- Modify: `docs/api/7-App接口.md`
- Modify: `docs/api/8-统计接口.md`

**Step 1: 查找项目中的错误码定义**

```bash
grep -rn "ErrorCode\|error_code\|ResultCode\|code.*=" video/src/main/java --include="*.java" | head -30
```

**Step 2: 为每个 API 文档添加「认证与错误」章节**

在每篇 API 文档开头添加：

```markdown
## 认证

除标注「公开」的接口外，所有接口需要在请求头携带 `token` 字段。

## 通用错误响应

| code | 说明 |
|------|------|
| 0 | 成功 |
| 非0 | 失败，msg 字段包含错误描述 |

未携带或无效 token 时返回 HTTP 403。

### 错误示例

```json
{
  "code": 1,
  "msg": "videoType不能为空",
  "data": null
}
```
```

**Step 3: 标注每个接口是公开还是需认证**

在每个接口的标题旁加标记，如：
- `### POST /video/create 🔒` （需认证）
- `### GET /video/getVideoDetail` （公开）

**Step 4: 减少与业务文档的重叠**

API 文档中若有大段流程描述与业务文档重复，替换为链接引用：
```markdown
> 详细业务流程见 [视频上传与去重](../业务/1-视频上传与去重.md)
```

**Step 5: 提交**

```bash
git add docs/api/
git commit -m "docs: API文档补充认证说明和错误响应码

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 4: Plans 文档标记完成状态

**Files:**
- Modify: `docs/plans/2025-07-22-ux-improvements-design.md`
- Modify: `docs/plans/2025-07-23-e2e-testing-design.md`
- Modify: `docs/plans/2025-07-23-e2e-testing-plan.md`
- Modify: `docs/plans/2025-07-23-frontend-polish-plan.md`
- Modify: `docs/plans/2026-03-07-frontend-redesign-design.md`
- Modify: `docs/plans/2026-03-07-test-review-design.md`
- Modify: `docs/plans/2026-03-07-test-review-plan.md`

**Step 1: 在每个 plan 文件开头添加状态标记**

根据 PR 历史判断每个 plan 的完成状态：

```markdown
> **状态:** ✅ 已完成 — 通过 [PR #15](https://github.com/makewheels/video-2022/pull/15) 合并
```

或：

```markdown
> **状态:** ✅ 已完成 — 通过 PR #16, #18 合并
```

**对应关系：**
- `2025-07-22-ux-improvements-design.md` → PR #15 (UX优化)
- `2025-07-23-e2e-testing-design.md` → PR #16 (E2E测试)
- `2025-07-23-e2e-testing-plan.md` → PR #16 (E2E测试)
- `2025-07-23-frontend-polish-plan.md` → PR #17 (前端优化)
- `2026-03-07-frontend-redesign-design.md` → PR #14 (前端重设计)
- `2026-03-07-test-review-design.md` → PR #18 (测试套件改进)
- `2026-03-07-test-review-plan.md` → PR #18 (测试套件改进)

**Step 2: 提交**

```bash
git add docs/plans/
git commit -m "docs: 标记所有计划文档的完成状态

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 5: 关键设计文档增强 + 更新 CHANGELOG

**Files:**
- Modify: `docs/1-关键设计.md`
- Modify: `docs/CHANGELOG.md`

**Step 1: 关键设计文档添加源码目录结构**

在 `docs/1-关键设计.md` 的架构部分添加源码包结构：

```markdown
## 源码包结构

```
video/src/main/java/com/github/makewheels/video2022/
├── video/          # 视频核心（创建、修改、查询）
├── file/           # 文件管理（上传、MD5、访问日志）
├── transcode/      # 转码服务（MPS、云函数）
├── cover/          # 封面提取
├── watch/          # 播放服务
├── playlist/       # 播放列表
├── user/           # 用户认证
├── finance/        # 计费系统（钱包、账单、交易）
├── oss/            # 阿里云 OSS 操作
├── etc/            # 工具服务（Redis、钉钉、IP、统计、校验）
└── system/         # 系统（拦截器、异常处理、健康检查）
```
```

**Step 2: 更新 CHANGELOG 添加本次 PR 记录**

在 CHANGELOG.md 顶部添加本 PR 的记录。

**Step 3: 提交**

```bash
git add docs/1-关键设计.md docs/CHANGELOG.md
git commit -m "docs: 关键设计文档添加源码包结构 + 更新CHANGELOG

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 6: 推送并创建 PR

**Step 1: 推送分支**

```bash
git push origin docs/doc-review
```

**Step 2: 创建 PR**

```bash
gh pr create --title "docs: 文档全面评审与改进" \
  --body "## 变更内容

### README 清理
- 删除与 CHANGELOG 重复的 PR 表格
- 修复指向已归档文档的断链

### 业务文档增强
- 10 篇业务文档添加「源码位置」引用表
- 修正与代码不一致的技术描述

### API 文档补充
- 8 篇 API 文档添加认证说明和错误响应码
- 标注公开/认证接口
- 减少与业务文档的内容重叠

### Plans 状态标记
- 7 个计划文档添加完成状态和对应 PR 链接

### 关键设计增强
- 添加源码包结构图
"
```
