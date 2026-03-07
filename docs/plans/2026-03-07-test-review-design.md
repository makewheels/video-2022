> **状态:** ✅ 已完成 — [PR #18](https://github.com/makewheels/video-2022/pull/18)

# 测试套件全面改进设计文档

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 全面改进项目测试质量 — 清理垃圾测试、增强现有测试、补充缺失测试、升级前端行为测试

**Architecture:** 分层推进，每层独立可验证：清理 → 增强 → 补充 → 升级

**Tech Stack:** JUnit 5, Spring Boot Test, @MockitoBean, Playwright (desktop/mobile/tablet)

---

## 现状分析

### Java 测试（28 文件）
- **7 个高质量集成测试：** CoverServiceTest(15), UserServiceTest(14), BillingServiceTest(14), PlaylistServiceTest(19), VideoCreateServiceTest(12), TranscodeCallbackServiceTest(9), HeartbeatProgressTest(9)
- **2 个高质量单元测试：** OssPathUtilTest(23), IdServiceTest(10)
- **5 个 E2E 测试：** LoginE2ETest(4), VideoUploadE2ETest(2), VideoModifyE2ETest(2), VideoWatchE2ETest(3), PlaylistE2ETest(2)
- **1 个场景测试：** UploadFlowTest(1)
- **2 个基类：** BaseIntegrationTest, BaseE2ETest
- **11 个垃圾测试：** 无断言的手动调试脚本

### Playwright 测试（8 文件）
- 主要是 DOM 结构检测（元素是否存在、CSS 属性）
- 缺少真实用户行为测试

### 未测试的 Service（30+ 个）
47 个 Service 中仅 ~10 个有测试覆盖

---

## 改进计划

### 第一层：清理垃圾测试
删除 11 个无断言/手动调试文件：
- TestDing, TestUpload, TestId, TestIp, TestThreadPool
- GzUnzipTest, OssLogTest, OssInventoryTest, SmokeTest
- JavaFunctionLineCounterTest, CreateBillTaskTests

### 第二层：增强现有测试
为已有测试补充错误场景和边界用例：
- E2E 测试：无效参数、权限拒绝、边界条件
- 集成测试：空值、超长输入、零/负数金额、并发场景

### 第三层：补充缺失 Service 测试
按业务风险排序，为 ~20 个关键 Service 新增测试：

**高优先级：** CheckService, WatchService, StatisticsService, RawFileService, VideoReadyService, VideoService
**中优先级：** SessionService, ClientService, LinkService, FileAccessLogService, FileChangeService, FileService
**低优先级（Mock 外部 API）：** AliyunMpsService, CloudFunctionTranscodeService, YoutubeService, OssService 系列, WalletService, NotificationService/DingService, TransactionService, UnitPriceService, RedisService, IpService

### 第四层：Playwright 行为测试升级
从 DOM 检测升级到用户行为测试：
- 登录流程端到端
- 导航交互（汉堡菜单、主题切换）
- 表单验证（错误提示、必填字段）
- 页面跳转验证

---

## 测试质量标准

一个好的测试应该：
1. **有明确的断言** — 不只是"不报错"，要验证具体值
2. **覆盖正向和反向路径** — 成功和失败都要测
3. **测试边界条件** — 空值、极值、格式错误
4. **独立可重复** — 不依赖其他测试的执行顺序
5. **命名清晰** — 从方法名就能知道测的是什么场景
6. **快速反馈** — 单个测试不超过 5 秒
