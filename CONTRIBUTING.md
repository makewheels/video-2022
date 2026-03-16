# 贡献指南 — video-2022 开发流程规范

本文档定义了 video-2022 项目的标准开发流程。所有新功能、Bug 修复、重构都必须遵循此流程。

---

## 一、开发前准备

### 1.1 阅读相关文档（必须）

**在写任何代码之前**，先阅读以下文档：

| 文档 | 路径 | 说明 |
|------|------|------|
| 项目 README | `README.md` | 架构总览、技术栈、构建方式 |
| 业务文档 | `docs/业务/*.md` | 13 个业务模块详细设计 |
| API 文档 | `docs/api/*.md` | 10 个接口规范 |
| 测试文档 | `docs/测试/*.md` | 测试策略和用例说明 |
| 变更日志 | `docs/CHANGELOG.md` | 已有功能和历史变更 |

**为什么必须先读文档？**
- 避免重复实现已有功能
- 理解现有数据模型和接口设计
- 确保新功能与现有架构一致
- 了解测试覆盖现状

### 1.2 理解项目结构

```
video-2022/
├── server/video/       # Java Spring Boot 后端（核心服务）
├── web/            # React + TypeScript 前端
├── android/             # Kotlin + Jetpack Compose 安卓客户端
├── ios/                 # Swift + SwiftUI iOS 客户端
├── cli/                 # Python 命令行工具
├── console/    # React 开发者门户
├── test/                # Python E2E 测试（API + Browser）
├── docs/                # 项目文档
├── scripts/             # 部署和运维脚本
└── .github/workflows/   # CI/CD 流水线
```

---

## 二、分支和 PR 规范

### 2.1 分支命名

```
feature/xxx     # 新功能
fix/xxx         # Bug 修复
refactor/xxx    # 重构
docs/xxx        # 文档
test/xxx        # 测试
improve/xxx     # 优化改进
```

### 2.2 PR 流程

1. 基于最新 `master` 创建分支
2. 开发 + 本地测试通过
3. 推送分支到 GitHub
4. 创建 PR → CI 自动运行（9 个 Job）
5. CI 全部通过后合并
6. 合并到 master 后自动部署到服务器

### 2.3 CI 流水线（9 个 Job）

| # | Job | 说明 |
|---|-----|------|
| 1 | Backend 单元测试 | `mvn test`（~440 个测试） |
| 2 | Frontend 单元测试 | `npm run test`（Vitest） |
| 3 | CLI 单元测试 | `pytest cli/`（89 个测试） |
| 4 | Android 单元测试 | `./gradlew testDebugUnitTest`（~13 个测试） |
| 5 | iOS 单元测试 | `xcodebuild test`（~51 个测试） |
| 6 | 构建 E2E 产物 | 构建前端 + 后端 JAR |
| 7 | API E2E 测试 | `pytest test/api/`（75 个测试） |
| 8 | Browser E2E 测试 | Playwright 浏览器自动化测试 |
| 9 | 最终汇总 | 检查所有 Job 通过 |

**所有 9 个 Job 必须通过才能合并。**

---

## 三、多端同步修改规范

### 3.1 修改影响矩阵

新增或修改一个后端接口时，需要同步更新的组件：

```
后端接口变更
 ├── 1. 后端 Controller / Service / Repository / Entity
 ├── 2. 后端单元测试
 ├── 3. OpenAPI 注解（@Tag, @Operation, @Schema）
 ├── 4. Android API 接口（Retrofit interface）
 ├── 5. Android 单元测试
 ├── 6. iOS API 调用（APIClient）
 ├── 7. iOS 单元测试
 ├── 8. 前端 API 调用（api.ts）
 ├── 9. 前端单元测试
 ├── 10. CLI 命令（如果相关）
 ├── 11. 开发者门户（如果是 OpenAPI 接口）
 ├── 12. E2E 测试
 ├── 13. 业务文档
 ├── 14. API 文档
 └── 15. CHANGELOG.md
```

### 3.2 判断影响范围

| 变更类型 | 影响范围 |
|----------|----------|
| 新增后端接口 | 后端 + 对应客户端 + 测试 + 文档 |
| 修改接口响应 | 后端 + 所有调用该接口的客户端 + 测试 |
| 新增数据库字段 | 后端 Entity + 可能的客户端 Model |
| 纯前端修改 | 前端 + 前端测试 |
| 纯文档修改 | 只改文档 |
| 配置变更 | 后端 + 可能影响部署 |

### 3.3 一个功能一个 PR

**原则：** 每个功能、修复、重构必须是独立的 PR，不要混合多个不相关的变更。

---

## 四、单元测试要求

### 4.1 新增代码必须有测试

| 组件 | 测试框架 | 测试位置 |
|------|----------|----------|
| 后端 Service | JUnit 5 + @SpringBootTest | `server/video/src/test/java/` |
| 后端 Controller | JUnit 5 + MockMvc/集成测试 | 同上 |
| Android ViewModel | JUnit 4 + MockK + Turbine | `android/app/src/test/java/` |
| iOS Service/Model | XCTest | `ios/VideoAppTests/` |
| 前端组件/页面 | Vitest + Testing Library | `web/src/**/*.test.tsx` |
| CLI 命令 | pytest | `cli/tests/` |
| E2E | pytest + Playwright | `test/` |

### 4.2 后端测试模式

```java
class XxxServiceTest extends BaseIntegrationTest {
    @Autowired private XxxService xxxService;

    @BeforeEach void setUp() { cleanDatabase(); }
    @AfterEach void tearDown() { cleanDatabase(); }

    @Test void testXxx() {
        // 准备数据 → 执行操作 → 断言结果
    }
}
```

**BaseIntegrationTest 提供：**
- 真实 MongoDB（video-2022-test 数据库）
- Mock 所有外部服务（OSS、MPS、IP 等）
- `cleanDatabase()` 清理所有集合

### 4.3 测试覆盖要求

- **新功能：** 100% 核心逻辑必须有测试
- **Bug 修复：** 必须附带复现 Bug 的测试用例
- **重构：** 现有测试不能减少

---

## 五、OpenAPI 接口修改规范

### 5.1 接口注解

所有 OpenAPI 相关的 Controller 必须有：

```java
@Tag(name = "模块名", description = "模块描述")
@RestController
public class XxxController {

    @Operation(summary = "接口名", description = "详细说明")
    @GetMapping("path")
    public Result<XxxResponse> method() { ... }
}
```

### 5.2 DTO 注解

```java
@Data
public class XxxRequest {
    @Schema(description = "字段描述", example = "示例值")
    private String field;
}
```

### 5.3 Redoc 文档

修改 OpenAPI 接口后，在本地验证 Redoc 文档是否正确渲染：
1. 启动后端 `mvn spring-boot:run`
2. 访问 `http://localhost:5022/api/v1/openapi` 查看 JSON 规范
3. 确认新接口出现在文档中

---

## 六、部署影响评估

### 6.1 不影响部署的变更
- 纯文档修改
- 纯前端修改（会自动构建部署）
- 新增测试
- 新增不影响已有接口的后端代码

### 6.2 需要评估的变更

| 变更 | 需确认 |
|------|--------|
| 新增 MongoDB 字段 | 是否需要数据迁移？ |
| 修改 properties 配置 | 服务器 `.env` 是否需要更新？ |
| 新增环境变量 | 是否已添加到 `.env.example` 和服务器 `.env`？ |
| 新增依赖 | 是否增加 JAR 体积？是否有安全问题？ |
| 修改接口路径 | 是否有客户端还在用旧路径？ |
| 修改 Nginx 配置 | 是否需要更新 `scripts/deploy.sh`？ |

### 6.3 部署流程

```
push to master → CI 测试 → 构建 Docker 镜像 → 推送阿里云镜像仓库
                         → 构建控制台 → 部署控制台静态文件
                                      → Docker pull → 启动容器 → 健康检查
```

- Dockerfile：`Dockerfile`（多阶段构建：Node→Maven→JRE）
- 部署脚本：`scripts/deploy.sh`（Docker pull + run）
- 部署流水线：`.github/workflows/deploy.yml`
- Docker 镜像：`registry.cn-beijing.aliyuncs.com/b4/video-2022:<时间戳>`
- **禁止直接在服务器上操作代码，所有变更必须通过 Git + CI**

---

## 七、文档更新规范

### 7.1 每个 PR 必须更新

| 文档 | 什么时候更新 |
|------|------------|
| `docs/CHANGELOG.md` | 每个 PR 合并前 |
| `docs/业务/*.md` | 业务逻辑变更时 |
| `docs/api/*.md` | 接口变更时 |
| `docs/测试/*.md` | 测试策略变更时 |
| `README.md` | 架构或技术栈变更时 |

### 7.2 CHANGELOG 格式

```markdown
### [PR #XX](https://github.com/makewheels/video-2022/pull/XX) — 简述
- 变更点 1
- 变更点 2
- 测试结果
```

---

## 八、本地开发环境搭建

### 8.1 环境要求

- Java 21 + Maven 3.9+
- Node.js 20+ + npm
- Python 3.12+ + pip
- MongoDB 6.0+（本地或 Docker）
- Xcode 16+（iOS 开发，仅 macOS）
- Android Studio（Android 开发）

### 8.2 快速开始

```bash
# 1. 克隆
git clone https://github.com/makewheels/video-2022.git
cd video-2022

# 2. 配置环境变量
cp .env.example server/video/.env
# 编辑 .env 填入 MongoDB 连接信息等

# 3. 后端
cd server/video && mvn test        # 运行测试
mvn spring-boot:run                 # 启动服务（端口 5022）

# 4. 前端
cd web && npm install && npm run dev   # 启动开发服务器

# 5. Android
cd android && ./gradlew testDebugUnitTest   # 测试

# 6. iOS
cd ios && xcodegen generate                  # 生成 Xcode 项目
xcodebuild test -project VideoApp.xcodeproj -scheme VideoApp ...
```

---

## 九、常见问题

### Q: CI 中某个 Job 失败了怎么办？
A: 查看 GitHub Actions 日志，修复问题后推送新 commit，CI 会重新运行。

### Q: 部署后服务不正常怎么办？
A: SSH 到服务器，运行 `docker logs video-2022` 查看容器日志。检查 `scripts/deploy.sh` 的健康检查步骤。

### Q: 修改了后端接口，忘了更新客户端怎么办？
A: E2E 测试会覆盖大部分场景。但最好参照本文档第三节的影响矩阵，逐一检查。

### Q: 新增了环境变量，如何通知其他开发者？
A: 更新 `.env.example`，在 PR 描述中注明，CHANGELOG 中记录。

---

## 十、代码审查清单

提交 PR 前自查：

- [ ] 分支是基于最新 master 创建的
- [ ] 所有新代码有单元测试
- [ ] 本地 `mvn test` 通过
- [ ] 本地 Android/iOS 测试通过（如果改了移动端）
- [ ] CHANGELOG.md 已更新
- [ ] 相关文档已更新
- [ ] 没有硬编码的密钥/密码
- [ ] 没有引入未使用的依赖
- [ ] 没有遗留 TODO/FIXME（除非有合理理由）
- [ ] Commit message 格式正确：`type: 描述`
