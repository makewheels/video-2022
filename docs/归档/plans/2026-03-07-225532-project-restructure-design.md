# 项目重构设计：前端/后端/测试分离

## 目标

将项目拆分为三个独立子项目：前端（React）、后端（Java Spring Boot）、测试（Python），各自可以独立运行和开发。

## 代码规范

- 文件不超过 500 行（软限制）
- 函数不超过 50 行（软限制）

---

## 1. 目录结构

```
video-2022/
├── frontend/                      ← Vite + React 前端项目
│   ├── src/
│   │   ├── components/            ← 可复用组件
│   │   ├── pages/                 ← 页面组件
│   │   ├── hooks/                 ← 自定义 hooks
│   │   ├── utils/                 ← 工具函数（API、auth、toast）
│   │   ├── styles/                ← CSS（基于现有 global.css）
│   │   ├── App.tsx                ← 根组件 + 路由
│   │   └── main.tsx               ← 入口
│   ├── public/                    ← 静态资源
│   ├── tests/                     ← Vitest 前端单元测试
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   └── README.md
│
├── backend/                       ← Java Spring Boot 后端项目
│   ├── video/                     ← 主服务模块
│   │   ├── src/main/java/         ← 业务代码
│   │   ├── src/main/resources/    ← 配置 + 前端 build 产出
│   │   ├── src/test/java/         ← Java 单元/集成测试
│   │   └── pom.xml
│   ├── youtube/                   ← YouTube 服务模块
│   │   ├── src/
│   │   └── pom.xml
│   ├── pom.xml                    ← Maven parent
│   └── README.md
│
├── test/                          ← Python 端到端测试项目
│   ├── api/                       ← API 级 E2E 测试
│   │   ├── test_login.py
│   │   ├── test_video_upload.py
│   │   ├── test_video_modify.py
│   │   ├── test_video_watch.py
│   │   └── test_playlist.py
│   ├── browser/                   ← 浏览器级 E2E 测试
│   │   ├── test_login_page.py
│   │   ├── test_upload_page.py
│   │   ├── test_watch_page.py
│   │   ├── test_my_videos.py
│   │   ├── test_responsive.py
│   │   └── test_theme.py
│   ├── conftest.py                ← 共享 fixtures
│   ├── requirements.txt
│   ├── pytest.ini
│   └── README.md
│
├── docs/                          ← 文档
├── .github/                       ← CI/CD
└── README.md
```

## 2. 前端设计（frontend/）

### 技术栈

| 类别 | 选择 | 说明 |
|------|------|------|
| 构建工具 | Vite | 快速开发服务器 + 生产构建 |
| 框架 | React 18 | 主流，AI 友好 |
| 语言 | TypeScript | 类型安全 |
| 路由 | React Router v6 | SPA 路由 |
| HTTP | Axios | 保持现有 API 调用方式 |
| 状态管理 | React Context | 项目规模不需要 Redux |
| 播放器 | Video.js + React wrapper | 保持现有播放器功能 |
| 图表 | ECharts for React | 统计页使用 |
| 单元测试 | Vitest + React Testing Library | 组件测试 |

### 页面路由

| 路由 | 组件 | 对应现有页面 |
|------|------|-------------|
| `/login` | LoginPage | login.html |
| `/auth/callback` | AuthCallback | save-token.html |
| `/` | MyVideosPage | index.html |
| `/upload` | UploadPage | upload.html |
| `/edit/:videoId` | EditPage | edit.html |
| `/watch/:videoId` | WatchPage | watch.html |
| `/statistics` | StatisticsPage | statistics.html |
| `/youtube` | YouTubePage | transfer-youtube.html |

### 构建产出

`npm run build` 输出到 `backend/video/src/main/resources/static/`，Spring Boot 直接 serve。

### 前端单元测试（Vitest）

测试组件渲染、用户交互、API mock：
- 登录表单验证
- 视频列表渲染与搜索
- 评论组件交互
- 点赞/踩切换
- 主题切换
- 响应式布局判断

## 3. 后端改动（backend/）

### 目录迁移

- `video/` → `backend/video/`
- `youtube/` → `backend/youtube/`
- `pom.xml` → `backend/pom.xml`

### 代码改动

1. **删除 `WatchPageController`**：不再需要服务端渲染
2. **添加 SPA 路由 fallback**：非 API 请求返回 `index.html`
3. **删除旧前端文件**：`src/main/resources/static/` 和 `templates/` 下的 HTML/JS/CSS
4. **删除 `src/test/playwright/`**：迁移到 test/
5. **删除 Java E2E 测试**：迁移到 test/ 的 Python 版本
6. **API 路径不变**：所有现有 API 保持原样

### 保留的测试

Java 单元/集成测试（446 个）保留在 `backend/video/src/test/java/` 中，因为它们需要 import Java 类。

## 4. 测试设计（test/）

### 技术栈

| 类别 | 选择 |
|------|------|
| 语言 | Python 3.12+ |
| 测试框架 | pytest |
| HTTP 客户端 | requests |
| 浏览器自动化 | Playwright for Python |
| 配置 | pytest.ini + conftest.py |

### 共享 fixtures（conftest.py）

```python
@pytest.fixture
def base_url():
    return os.getenv("BASE_URL", "http://localhost:5022")

@pytest.fixture
def auth_token(base_url):
    # 登录获取 token

@pytest.fixture
def api_client(base_url, auth_token):
    # 带 token 的 requests.Session

@pytest.fixture
def browser():
    # Playwright browser 实例
```

### API 测试迁移对照

| 原 Java E2E | Python 测试 | 用例数 |
|-------------|------------|--------|
| LoginE2ETest | test_login.py | 5+ |
| VideoUploadE2ETest | test_video_upload.py | 4+ |
| VideoModifyE2ETest | test_video_modify.py | 4+ |
| VideoWatchE2ETest | test_video_watch.py | 4+ |
| PlaylistE2ETest | test_playlist.py | 5+ |

### 浏览器测试迁移对照

| 原 JS Playwright | Python 测试 | 覆盖范围 |
|------------------|------------|---------|
| login.spec.js | test_login_page.py | 登录页交互 |
| upload.spec.js | test_upload_page.py | 上传页交互 |
| watch.spec.js + player.spec.js | test_watch_page.py | 观看页 + 播放器 |
| e2e-flow.spec.js + behavior.spec.js | test_my_videos.py | 视频列表 + 导航 |
| frontend-polish.spec.js + global.spec.js | test_responsive.py | 响应式 + 布局 |
| ux.spec.js + minor-pages.spec.js | test_theme.py | 主题 + UX |

## 5. CI/CD 流水线

```yaml
jobs:
  unit-tests:           # Java 单元/集成测试
    # mvn test（排除 e2e）
    # 服务：MongoDB 7 + Redis 7

  frontend-tests:       # 前端单元测试
    # cd frontend && npm test

  api-e2e-tests:        # Python API E2E 测试
    # 启动 Spring Boot → pytest test/api/
    # 服务：MongoDB 7 + Redis 7

  browser-e2e-tests:    # Python 浏览器 E2E 测试
    # 启动 Spring Boot → pytest test/browser/
    # 服务：MongoDB 7 + Redis 7
    # 安装 Playwright browsers
```

## 6. 迁移阶段

1. **目录重组**：移动 video/ youtube/ pom.xml → backend/，创建 frontend/ test/ 骨架，更新 CI
2. **React 基础搭建**：Vite 项目初始化，全局布局，路由，登录页
3. **页面迁移**：逐个迁移 8 个页面到 React 组件
4. **Python 测试搭建**：初始化 pytest，迁移 API E2E 测试
5. **浏览器测试迁移**：Playwright Python 测试，迁移所有浏览器测试
6. **清理**：删旧前端文件、旧测试代码，更新文档
