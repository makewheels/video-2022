# 项目重构实施计划：前端/后端/测试分离

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 video-2022 拆分为三个独立子项目（frontend/React, backend/Java, test/Python），迁移所有前端页面到 React，迁移所有 E2E 测试到 Python。

**Architecture:** 根目录三子项目结构。前端用 Vite + React + TypeScript，构建产出到 backend static 目录。测试用 Python + pytest + Playwright + requests。后端保留现有 Java Spring Boot 结构，仅移动目录。

**Tech Stack:** React 18, Vite, TypeScript, React Router v6, Video.js, Axios, Vitest, Python 3.12+, pytest, Playwright for Python, requests

**代码规范:** 文件不超过 500 行，函数不超过 50 行（软限制）

---

## Phase 1: 目录重组

### Task 1: 创建 backend 目录并移动文件

**Files:**
- Move: `video/` → `backend/video/`
- Move: `youtube/` → `backend/youtube/`
- Move: `pom.xml` → `backend/pom.xml`

**Step 1: 移动目录**

```bash
mkdir backend
git mv video backend/video
git mv youtube backend/youtube
git mv pom.xml backend/pom.xml
```

**Step 2: 移动根目录其他后端文件**

```bash
# .env 如果存在也要移动
[ -f .env ] && git mv .env backend/.env
```

**Step 3: 提交**

```bash
git add -A && git commit -m "refactor: move video/ youtube/ pom.xml into backend/"
```

---

### Task 2: 修复 Maven 构建路径

**Files:**
- Modify: `backend/pom.xml` — modules 路径不变（仍是 `video` 和 `youtube`）
- Modify: `.github/workflows/ci.yml` — 所有 Maven 命令加 `-f backend/pom.xml` 或 `cd backend`

**Step 1: 更新 CI 工作流路径**

`.github/workflows/ci.yml` 中所有 `mvn` 命令需要在 `backend/` 目录下执行。

找到所有 `mvn test` / `mvn package` 命令，在前面加 `cd backend && `，或改用 `working-directory: backend`。

同时 `.env` 路径也要更新（如果 CI 中用到）。

**Step 2: 验证 Maven 构建**

```bash
cd backend && export $(grep -v '^#' .env | grep -v '^$' | xargs) && mvn clean package -pl video -Pspringboot -Dmaven.test.skip=true -q
```

Expected: BUILD SUCCESS

**Step 3: 验证 Java 测试**

```bash
cd backend && export $(grep -v '^#' .env | grep -v '^$' | xargs) && mvn test -pl video -Pspringboot
```

Expected: 468 tests pass (446 unit/integration + 22 E2E)

**Step 4: 提交**

```bash
git add -A && git commit -m "fix: update CI and build paths for backend/ directory"
```

---

### Task 3: 创建 frontend 骨架

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/index.html`
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/App.tsx`
- Create: `frontend/README.md`

**Step 1: 初始化 Vite + React + TypeScript 项目**

```bash
cd frontend
npm create vite@latest . -- --template react-ts
npm install
```

**Step 2: 安装核心依赖**

```bash
npm install react-router-dom axios video.js @videojs/themes
npm install -D vitest @testing-library/react @testing-library/jest-dom jsdom
```

**Step 3: 配置 Vite 构建产出到 backend static**

修改 `frontend/vite.config.ts`：

```typescript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../backend/video/src/main/resources/static',
    emptyOutDir: true,
  },
  server: {
    proxy: {
      '/video': 'http://localhost:5022',
      '/comment': 'http://localhost:5022',
      '/videoLike': 'http://localhost:5022',
      '/user': 'http://localhost:5022',
      '/file': 'http://localhost:5022',
      '/watchController': 'http://localhost:5022',
      '/playlist': 'http://localhost:5022',
      '/heartbeat': 'http://localhost:5022',
      '/progress': 'http://localhost:5022',
      '/statistics': 'http://localhost:5022',
      '/session': 'http://localhost:5022',
      '/client': 'http://localhost:5022',
    },
  },
})
```

**Step 4: 配置 Vitest**

在 `vite.config.ts` 添加：

```typescript
/// <reference types="vitest" />
// 在 defineConfig 中添加：
test: {
  globals: true,
  environment: 'jsdom',
  setupFiles: './tests/setup.ts',
},
```

创建 `frontend/tests/setup.ts`：

```typescript
import '@testing-library/jest-dom'
```

**Step 5: 验证前端能启动**

```bash
cd frontend && npm run dev
# 浏览器访问 http://localhost:5173 应该看到 Vite + React 默认页
```

**Step 6: 验证构建产出**

```bash
cd frontend && npm run build
ls ../backend/video/src/main/resources/static/
# 应该看到 index.html, assets/ 等
```

**Step 7: 提交**

```bash
git add -A && git commit -m "feat: initialize frontend/ with Vite + React + TypeScript"
```

---

### Task 4: 创建 test 骨架

**Files:**
- Create: `test/requirements.txt`
- Create: `test/pytest.ini`
- Create: `test/conftest.py`
- Create: `test/api/__init__.py`
- Create: `test/browser/__init__.py`
- Create: `test/README.md`

**Step 1: 创建目录和配置文件**

`test/requirements.txt`:
```
pytest>=8.0
pytest-playwright>=0.5
playwright>=1.40
requests>=2.31
```

`test/pytest.ini`:
```ini
[pytest]
testpaths = api browser
python_files = test_*.py
python_classes = Test*
python_functions = test_*
markers =
    api: API-level E2E tests
    browser: Browser-level E2E tests
```

**Step 2: 创建 conftest.py**

```python
import os
import pytest
import requests

BASE_URL = os.getenv("BASE_URL", "http://localhost:5022")
TEST_PHONE = "19900001111"
TEST_CODE = "111"


@pytest.fixture(scope="session")
def base_url():
    return BASE_URL


@pytest.fixture(scope="session")
def auth_token(base_url):
    resp = requests.get(
        f"{base_url}/user/requestVerificationCode",
        params={"phoneNumber": TEST_PHONE},
    )
    assert resp.status_code == 200
    resp = requests.get(
        f"{base_url}/user/submitVerificationCode",
        params={"phoneNumber": TEST_PHONE, "verificationCode": TEST_CODE},
    )
    assert resp.status_code == 200
    data = resp.json()
    return data["data"]["token"]


@pytest.fixture(scope="session")
def api_client(base_url, auth_token):
    session = requests.Session()
    session.headers.update({"token": auth_token})
    session.base_url = base_url
    return session
```

**Step 3: 安装依赖并验证**

```bash
cd test
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
playwright install chromium
pytest --co  # collect tests (should find 0 initially)
```

**Step 4: 提交**

```bash
git add -A && git commit -m "feat: initialize test/ with Python pytest + Playwright skeleton"
```

---

## Phase 2: React 前端基础

### Task 5: 全局布局和工具函数

**Files:**
- Create: `frontend/src/utils/api.ts` — Axios 实例 + 拦截器
- Create: `frontend/src/utils/auth.ts` — token 管理
- Create: `frontend/src/utils/toast.ts` — Toast 通知
- Create: `frontend/src/utils/theme.ts` — 主题切换
- Create: `frontend/src/components/Layout.tsx` — 全局布局（导航栏 + 内容）
- Create: `frontend/src/components/NavBar.tsx` — 导航栏组件
- Create: `frontend/src/components/Toast.tsx` — Toast 组件
- Migrate: `frontend/src/styles/global.css` ← 现有 `backend/video/src/main/resources/static/css/global.css`

**Step 1: 迁移 global.css**

从现有 `backend/video/src/main/resources/static/css/global.css` 复制到 `frontend/src/styles/global.css`，保留所有 CSS 变量和样式。

**Step 2: 创建工具函数**

参考现有 `backend/video/src/main/resources/static/js/global.js` 的逻辑：
- `api.ts`: 创建 Axios 实例，自动附加 token header
- `auth.ts`: getToken(), setToken(), removeToken(), isLoggedIn(), requireAuth()
- `toast.ts`: React Context + Provider，info/success/error 类型
- `theme.ts`: 读写 localStorage 主题，切换 CSS class

**Step 3: 创建布局组件**

`Layout.tsx`: 包含 NavBar + 主内容区 + Toast Provider
`NavBar.tsx`: Logo、导航链接、主题切换按钮、登录状态

参考现有 `global.js` 中的 `initNavMenu()` 逻辑。

**Step 4: 设置路由**

`App.tsx`:
```tsx
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import Layout from './components/Layout'
// import pages...

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<MyVideosPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/upload" element={<UploadPage />} />
          <Route path="/edit/:videoId" element={<EditPage />} />
          <Route path="/watch/:videoId" element={<WatchPage />} />
          <Route path="/statistics" element={<StatisticsPage />} />
          <Route path="/youtube" element={<YouTubePage />} />
          <Route path="/auth/callback" element={<AuthCallback />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
```

**Step 5: 写单元测试**

```typescript
// frontend/tests/utils/auth.test.ts
describe('auth utils', () => {
  test('getToken returns null when not logged in', () => { ... })
  test('setToken stores token in localStorage', () => { ... })
  test('isLoggedIn returns true when token exists', () => { ... })
})
```

**Step 6: 验证**

```bash
cd frontend && npm run dev
# 访问 http://localhost:5173，应该看到布局框架
cd frontend && npx vitest run
# 单元测试应该通过
```

**Step 7: 提交**

```bash
git add -A && git commit -m "feat: frontend global layout, utils, and routing"
```

---

### Task 6: 登录页

**Files:**
- Create: `frontend/src/pages/LoginPage.tsx`
- Create: `frontend/src/pages/AuthCallback.tsx`
- Create: `frontend/tests/pages/LoginPage.test.tsx`

**Step 1: 实现 LoginPage**

参考现有 `backend/video/src/main/resources/static/login.html`：
- 手机号输入框 + 发送验证码按钮（60s 倒计时）
- 验证码输入框 + 登录按钮
- 调用 `/user/requestVerificationCode` 和 `/user/submitVerificationCode`
- 登录成功后存 token、跳转首页

**Step 2: 实现 AuthCallback**

参考现有 `save-token.html`：
- 从 URL 参数读取 token
- 存入 localStorage
- 跳转到来源页或首页

**Step 3: 写单元测试**

- 测试手机号格式验证
- 测试倒计时逻辑
- 测试登录成功跳转

**Step 4: 验证**

```bash
cd frontend && npx vitest run
cd frontend && npm run dev  # 手动验证登录页面
```

**Step 5: 提交**

```bash
git add -A && git commit -m "feat: login page and auth callback"
```

---

### Task 7: 我的视频列表页

**Files:**
- Create: `frontend/src/pages/MyVideosPage.tsx`
- Create: `frontend/src/components/VideoCard.tsx`
- Create: `frontend/src/components/Pagination.tsx`
- Create: `frontend/tests/pages/MyVideosPage.test.tsx`

**Step 1: 实现 MyVideosPage**

参考现有 `backend/video/src/main/resources/static/index.html`：
- 搜索框（防抖 300ms）
- 视频列表（响应式：桌面表格/手机卡片）
- 每个视频显示：封面缩略图、标题、可见性图标、状态、时间、操作按钮
- 分页组件
- 删除确认弹窗
- 调用 `/video/getMyVideoList`

**Step 2: 写单元测试**

- 测试空列表渲染
- 测试搜索输入触发 API 调用
- 测试删除确认弹窗
- 测试分页切换

**Step 3: 验证并提交**

---

### Task 8: 上传页

**Files:**
- Create: `frontend/src/pages/UploadPage.tsx`
- Create: `frontend/src/components/FileUploader.tsx`
- Create: `frontend/src/components/VideoForm.tsx`
- Create: `frontend/tests/pages/UploadPage.test.tsx`

**Step 1: 实现 UploadPage**

参考现有 `upload.html`：
- 拖拽上传区域
- Aliyun OSS 直传（STS 凭证 → 分片上传）
- 上传进度条
- 上传完成后显示视频表单（标题、描述、可见性、播放列表）
- 转码状态轮询（5s/次，10min 超时）
- 调用 `/video/create`, `/file/getUploadCredentials`, `/file/uploadFinish`, `/video/getVideoStatus`

**Step 2: 安装 OSS SDK**

```bash
cd frontend && npm install ali-oss
```

**Step 3: 写单元测试并验证提交**

---

### Task 9: 编辑页

**Files:**
- Create: `frontend/src/pages/EditPage.tsx`
- Create: `frontend/tests/pages/EditPage.test.tsx`

**Step 1: 实现 EditPage**

参考现有 `edit.html`：
- 加载视频信息 (`/video/getVideoDetail`)
- 编辑表单（标题、描述、可见性）
- 保存按钮 (`/video/updateInfo`)
- 复制链接按钮
- 删除视频按钮（红色，确认弹窗）

**Step 2: 写单元测试并验证提交**

---

### Task 10: 观看页（最复杂）

**Files:**
- Create: `frontend/src/pages/WatchPage.tsx`
- Create: `frontend/src/components/VideoPlayer.tsx`
- Create: `frontend/src/components/CommentSection.tsx`
- Create: `frontend/src/components/CommentItem.tsx`
- Create: `frontend/src/components/LikeButtons.tsx`
- Create: `frontend/src/components/PlaylistSidebar.tsx`
- Create: `frontend/tests/pages/WatchPage.test.tsx`
- Create: `frontend/tests/components/CommentSection.test.tsx`
- Create: `frontend/tests/components/LikeButtons.test.tsx`

**Step 1: 安装播放器依赖**

```bash
cd frontend && npm install video.js @types/video.js
```

**Step 2: 实现 VideoPlayer 组件**

- Video.js 初始化（HLS 自适应码率）
- 播放器控件
- 心跳上报（每 5s 上报进度）
- 播放会话管理
- 时间定位（URL 参数 `t=`）

**Step 3: 实现 CommentSection**

参考现有 `watch.html` 评论区逻辑：
- 评论输入框
- 顶级评论列表（热门排序，分页）
- 回复列表（时间排序，分页）
- 评论点赞
- 删除评论（仅自己的）

**Step 4: 实现 LikeButtons**

- 👍 / 👎 按钮
- 点赞数显示
- 状态切换（like ↔ dislike ↔ none）

**Step 5: 实现 PlaylistSidebar**

- 播放列表选择
- 视频列表展示
- 上一个/下一个导航

**Step 6: 组装 WatchPage**

**Step 7: 写单元测试并验证提交**

---

### Task 11: 统计页和 YouTube 页

**Files:**
- Create: `frontend/src/pages/StatisticsPage.tsx`
- Create: `frontend/src/pages/YouTubePage.tsx`
- Create: `frontend/tests/pages/StatisticsPage.test.tsx`

**Step 1: 安装 ECharts**

```bash
cd frontend && npm install echarts echarts-for-react
```

**Step 2: 实现 StatisticsPage**

参考现有 `statistics.html`：
- 日期范围选择（7天/30天）
- ECharts 折线图（播放量趋势）
- 调用 `/statistics/aggregateTrafficData`

**Step 3: 实现 YouTubePage**

参考现有 `transfer-youtube.html`：
- YouTube URL 输入框
- 导入按钮
- 进度展示

**Step 4: 写单元测试并验证提交**

---

### Task 12: 后端 SPA 路由支持

**Files:**
- Modify: `backend/video/src/main/java/.../VideoApplication.java` 或新建 `WebConfig.java`
- Delete: `backend/video/src/main/java/.../watch/WatchPageController.java`
- Delete: `backend/video/src/main/resources/static/` (旧前端文件)
- Delete: `backend/video/src/main/resources/templates/watch.html`

**Step 1: 添加 SPA fallback controller**

创建 `SpaController.java`：

```java
@Controller
public class SpaController {
    @RequestMapping(value = {"/", "/login", "/upload", "/edit/**",
        "/watch/**", "/statistics", "/youtube", "/auth/**"})
    public String forward() {
        return "forward:/index.html";
    }
}
```

**Step 2: 删除 WatchPageController**

**Step 3: 删除旧前端文件**

```bash
cd backend/video/src/main/resources
rm -rf static/css static/js static/*.html static/watch
rm -rf templates/
```

**Step 4: 构建 React 并放入 static**

```bash
cd frontend && npm run build
```

**Step 5: 验证后端能 serve React 应用**

```bash
cd backend && export $(grep -v '^#' .env | grep -v '^$' | xargs) && java -jar video/target/video-0.0.1-SNAPSHOT.jar
# 访问 http://localhost:5022 应该看到 React 应用
```

**Step 6: 验证 Java 单元/集成测试仍然通过**

```bash
cd backend && export $(grep -v '^#' .env | grep -v '^$' | xargs) && mvn test -pl video -Pspringboot -Dtest='!com.github.makewheels.video2022.e2e.**'
```

Expected: 446 tests pass

**Step 7: 提交**

```bash
git add -A && git commit -m "feat: SPA routing support, remove old frontend files"
```

---

## Phase 3: Python API E2E 测试

### Task 13: 迁移登录测试

**Files:**
- Create: `test/api/test_login.py`

**Step 1: 写测试**

参考 `backend/video/src/test/java/.../e2e/LoginE2ETest.java`，用 Python requests 重写：

```python
import requests
import pytest

class TestLogin:
    def test_request_verification_code(self, base_url):
        resp = requests.get(f"{base_url}/user/requestVerificationCode",
                          params={"phoneNumber": "19900001111"})
        assert resp.status_code == 200
        assert resp.json()["code"] == "ok"

    def test_submit_verification_code(self, base_url):
        # 先请求验证码，再提交
        ...

    def test_token_validation(self, base_url, auth_token):
        resp = requests.get(f"{base_url}/user/getUserByToken",
                          headers={"token": auth_token})
        assert resp.status_code == 200

    def test_invalid_token_rejected(self, base_url):
        resp = requests.get(f"{base_url}/user/getUserByToken",
                          headers={"token": "invalid"})
        assert resp.status_code == 403

    def test_login_returns_user_info(self, base_url, auth_token):
        ...
```

**Step 2: 运行测试**

```bash
cd test && source venv/bin/activate
# 需要先启动后端服务
pytest api/test_login.py -v
```

Expected: 全部通过

**Step 3: 提交**

---

### Task 14: 迁移视频上传测试

**Files:**
- Create: `test/api/test_video_upload.py`

参考 `VideoUploadE2ETest.java`：
- test_create_video
- test_get_upload_credentials
- test_video_appears_in_list
- test_video_status_after_upload

---

### Task 15: 迁移视频修改测试

**Files:**
- Create: `test/api/test_video_modify.py`

参考 `VideoModifyE2ETest.java`：
- test_update_video_info
- test_other_user_cannot_update
- test_update_visibility
- test_update_title_description

---

### Task 16: 迁移视频观看测试

**Files:**
- Create: `test/api/test_video_watch.py`

参考 `VideoWatchE2ETest.java`：
- test_get_watch_info
- test_watch_page_access
- test_video_detail
- test_nonexistent_video_error

---

### Task 17: 迁移播放列表测试

**Files:**
- Create: `test/api/test_playlist.py`

参考 `PlaylistE2ETest.java`：
- test_create_playlist
- test_add_video_to_playlist
- test_multiple_videos
- test_empty_playlist
- test_missing_name

---

## Phase 4: Python 浏览器 E2E 测试

### Task 18: 浏览器测试基础设施

**Files:**
- Modify: `test/conftest.py` — 添加 Playwright fixtures
- Create: `test/browser/conftest.py` — 浏览器特定 fixtures

**Step 1: 更新 conftest.py**

添加 Playwright fixtures：

```python
from playwright.sync_api import sync_playwright

@pytest.fixture(scope="session")
def browser_instance():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        yield browser
        browser.close()

@pytest.fixture
def page(browser_instance, base_url):
    context = browser_instance.new_context()
    page = context.new_page()
    yield page
    context.close()

@pytest.fixture
def auth_page(browser_instance, base_url, auth_token):
    context = browser_instance.new_context(
        storage_state=None
    )
    page = context.new_page()
    # 注入 token 到 localStorage
    page.goto(base_url)
    page.evaluate(f"localStorage.setItem('token', '{auth_token}')")
    yield page
    context.close()
```

---

### Task 19: 迁移登录页浏览器测试

**Files:**
- Create: `test/browser/test_login_page.py`

参考 `login.spec.js`：
- test_login_page_renders
- test_phone_input_visible
- test_send_code_button
- test_login_flow_redirect
- test_mobile_responsive

---

### Task 20: 迁移上传页浏览器测试

**Files:**
- Create: `test/browser/test_upload_page.py`

参考 `upload.spec.js`：
- test_upload_page_requires_auth
- test_upload_zone_visible
- test_form_elements
- test_file_type_validation

---

### Task 21: 迁移观看页浏览器测试

**Files:**
- Create: `test/browser/test_watch_page.py`

参考 `watch.spec.js` + `player.spec.js`：
- test_player_container_visible
- test_video_info_displayed
- test_comment_section_visible
- test_like_buttons_visible
- test_responsive_layout

---

### Task 22: 迁移视频列表/行为测试

**Files:**
- Create: `test/browser/test_my_videos.py`

参考 `e2e-flow.spec.js` + `behavior.spec.js`：
- test_my_videos_page_loads
- test_navigation_links
- test_search_functionality
- test_mobile_hamburger_menu
- test_auth_redirect

---

### Task 23: 迁移响应式和主题测试

**Files:**
- Create: `test/browser/test_responsive.py`
- Create: `test/browser/test_theme.py`

参考 `frontend-polish.spec.js` + `global.spec.js` + `ux.spec.js`：
- test_desktop_layout
- test_mobile_layout
- test_tablet_layout
- test_theme_toggle
- test_toast_notifications
- test_footer_visible

---

## Phase 5: 清理与 CI

### Task 24: 删除旧测试文件

**Files:**
- Delete: `backend/video/src/test/playwright/` — 整个目录
- Delete: `backend/video/src/test/java/.../e2e/` — Java E2E 测试（已迁移到 Python）

**Step 1: 删除旧 Playwright 测试**

```bash
rm -rf backend/video/src/test/playwright
```

**Step 2: 删除 Java E2E 测试**

```bash
rm -rf backend/video/src/test/java/com/github/makewheels/video2022/e2e
```

**Step 3: 验证 Java 单元/集成测试仍通过**

```bash
cd backend && export $(grep -v '^#' .env | grep -v '^$' | xargs) && mvn test -pl video -Pspringboot
```

Expected: 446 tests pass（不再有 E2E 的 22 个）

**Step 4: 提交**

---

### Task 25: 更新 CI 流水线

**Files:**
- Modify: `.github/workflows/ci.yml`

**Step 1: 改为 4 个 job**

```yaml
name: CI

on:
  push:
    branches: [master]
  pull_request:
    branches: [master]

jobs:
  backend-tests:
    name: 后端单元/集成测试
    runs-on: ubuntu-latest
    services:
      mongo: { image: mongo:7, ports: ['27017:27017'] }
      redis: { image: redis:7, ports: ['6379:6379'] }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - run: cd backend && mvn test -pl video -Pspringboot

  frontend-tests:
    name: 前端单元测试
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20' }
      - run: cd frontend && npm ci && npx vitest run

  api-e2e-tests:
    name: API E2E 测试
    runs-on: ubuntu-latest
    services:
      mongo: { image: mongo:7, ports: ['27017:27017'] }
      redis: { image: redis:7, ports: ['6379:6379'] }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - uses: actions/setup-python@v5
        with: { python-version: '3.12' }
      - run: cd backend && mvn clean package -pl video -Pspringboot -Dmaven.test.skip=true -q
      - run: cd backend && java -jar video/target/video-0.0.1-SNAPSHOT.jar &
      - run: sleep 15  # 等待服务启动
      - run: cd test && pip install -r requirements.txt && pytest api/ -v

  browser-e2e-tests:
    name: 浏览器 E2E 测试
    runs-on: ubuntu-latest
    services:
      mongo: { image: mongo:7, ports: ['27017:27017'] }
      redis: { image: redis:7, ports: ['6379:6379'] }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - uses: actions/setup-node@v4
        with: { node-version: '20' }
      - uses: actions/setup-python@v5
        with: { python-version: '3.12' }
      - run: cd frontend && npm ci && npm run build
      - run: cd backend && mvn clean package -pl video -Pspringboot -Dmaven.test.skip=true -q
      - run: cd backend && java -jar video/target/video-0.0.1-SNAPSHOT.jar &
      - run: sleep 15
      - run: cd test && pip install -r requirements.txt && playwright install chromium --with-deps
      - run: cd test && pytest browser/ -v
```

**Step 2: 验证 CI 配置语法**

```bash
gh workflow view ci.yml
```

**Step 3: 提交**

---

### Task 26: 更新文档

**Files:**
- Modify: `docs/CHANGELOG.md`
- Modify: `docs/测试/README.md`
- Modify: `README.md`
- Modify: `frontend/README.md`
- Modify: `backend/README.md` (如果需要)
- Modify: `test/README.md`

**Step 1: 更新根 README**

添加项目结构说明和各子项目的快速入门。

**Step 2: 更新测试文档**

反映新的测试架构（Java 单元测试 + Vitest 前端测试 + Python E2E 测试）。

**Step 3: 更新 CHANGELOG**

**Step 4: 提交**

---

### Task 27: 最终验证

**Step 1: 验证后端测试**

```bash
cd backend && export $(grep -v '^#' .env | grep -v '^$' | xargs) && mvn test -pl video -Pspringboot
```

**Step 2: 验证前端测试**

```bash
cd frontend && npx vitest run
```

**Step 3: 验证 Python API 测试**

```bash
# 先启动后端
cd test && source venv/bin/activate && pytest api/ -v
```

**Step 4: 验证 Python 浏览器测试**

```bash
cd test && source venv/bin/activate && pytest browser/ -v
```

**Step 5: 全部通过后提交并推送**
