# Playwright 前端测试

Playwright 前端测试覆盖页面行为交互、端到端流程、前端样式规范、全局配置、各页面功能和 UX 组件。测试在浏览器环境中运行，验证真实的 DOM 交互和用户操作。

- **测试文件数**：10
- **测试用例数**：88

## 运行方式

```bash
# 运行全部 Playwright 测试
cd video/src/test/playwright && npx playwright test

# 运行单个测试文件
cd video/src/test/playwright && npx playwright test tests/login.spec.js

# 运行带 UI 的测试
cd video/src/test/playwright && npx playwright test --ui
```

---

## [behavior.spec.js](../../video/src/test/playwright/tests/behavior.spec.js)

验证页面核心交互行为，包括登录流程、导航切换、汉堡菜单、快捷操作卡片、上传页认证重定向和主题切换。

| # | 测试用例 | 场景 | 前置条件 | 操作 | 期望结果 | 测试目标 |
|---|---------|------|---------|------|---------|---------|
| 1 | `countdown starts after sending verification code` | 验证码倒计时 | 登录页 | 填写手机号 → 点击"获取验证码" | 按钮显示倒计时并禁用 | 倒计时行为 |
| 2 | `login button enables after code is sent` | 登录按钮启用 | 登录页 | 手机号填写 → 发送验证码 | 登录按钮从禁用变为启用 | 按钮状态联动 |
| 3 | `full login → redirect flow` | 完整登录跳转 | 登录页 | 填写手机号和验证码(111) → 提交 | 跳转离开登录页 | 登录重定向 |
| 4 | `clicking nav links navigates to correct pages` | 桌面导航点击 | 首页 | 点击"上传"导航链接 | 跳转到上传或登录页；点击"首页" → 首页 | 导航链接 |
| 5 | `active nav link updates on navigation` | 导航激活状态 | 登录页 | 首页链接未激活 → 跳转到首页 | 首页链接变为激活 | 激活状态跟踪 |
| 6 | `hamburger toggles menu and clicking link navigates` | 移动端汉堡菜单 | 375px 视口 | 汉堡按钮打开菜单 → 点击导航链接 | 页面跳转，菜单自动关闭 | 移动导航 |
| 7 | `hamburger toggles back to closed state` | 汉堡菜单开关 | 375px 视口 | 点击汉堡两次 | 文字在 ☰ 和 ✕ 之间切换 | 菜单状态切换 |
| 8 | `upload card navigates away from homepage` | 首页上传卡片 | 首页 | 点击上传快捷卡片 | 跳转到上传或登录页 | 快捷操作 |
| 9 | `youtube card click navigates away` | 首页 YouTube 卡片 | 首页 | 点击 YouTube 快捷卡片 | 跳转到 transfer-youtube 或登录页 | 快捷操作 |
| 10 | `unauthenticated visit redirects with target param` | 未认证重定向 | 未登录 | 访问 /upload.html | 重定向到 /login.html?target=... | 认证重定向 |
| 11 | `authenticated user stays on upload page` | 已认证访问 | localStorage 中有 token | 访问 /upload.html | 正常加载上传页 | 认证通过 |
| 12 | `toggle switches between light and dark` | 主题切换 | 首页 | 点击主题切换按钮 | data-theme 在 light/dark 之间切换 | 主题切换 |
| 13 | `theme persists across navigation` | 主题持久化 | 首页 | 切换到暗色 → 跳转到登录页 | 主题保持暗色 | 主题跨页持久化 |

---

## [e2e-flow.spec.js](../../video/src/test/playwright/tests/e2e-flow.spec.js)

端到端流程测试，验证真实的登录流程、手机号校验和页面可访问性。

| # | 测试用例 | 场景 | 前置条件 | 操作 | 期望结果 | 测试目标 |
|---|---------|------|---------|------|---------|---------|
| 1 | `输入手机号→发送验证码→登录→跳转` | 完整登录 E2E | 登录页 | 输入 13800138000 → 发送验证码 → 成功 toast → 输入 111 → 提交 | 成功 toast 或页面跳转 | 登录完整流程 |
| 2 | `手机号格式验证` | 无效手机号 | 登录页 | 输入 "123" → 发送验证码 | 错误 toast 提示格式错误 | 前端手机号校验 |
| 3 | `首页可访问且有导航链接` | 首页可访问 | 无 | 访问 /index.html | 200 状态码，桌面有导航链接，移动有汉堡按钮 | 首页可用性 |
| 4 | `未登录访问上传页重定向到登录页` | 上传页重定向 | 清空 cookies/storage | 访问 /upload.html | 重定向到 /login.html | 认证保护 |

---

## [frontend-polish.spec.js](../../video/src/test/playwright/tests/frontend-polish.spec.js)

前端样式规范和响应式布局测试。

| # | 测试用例 | 场景 | 前置条件 | 操作 | 期望结果 | 测试目标 |
|---|---------|------|---------|------|---------|---------|
| 1 | `desktop: nav links visible, hamburger hidden` | 桌面布局 | >768px 视口 | 检查导航 | 导航链接可见，汉堡按钮隐藏 | 桌面响应式 |
| 2 | `nav contains correct links` | 导航链接完整 | 首页 | 检查导航 | 包含首页、上传、统计、YouTube 4 个链接 | 导航完整性 |
| 3 | `active state set for current page` | 当前页面激活 | 首页 | 检查链接状态 | 当前页面链接含 active 类 | 激活状态 |
| 4 | `theme toggle button exists` | 主题切换存在 | 首页 | 检查 #theme-toggle | 可见 | 主题切换存在 |
| 5 | `header auth area exists` | 头部认证区域 | 首页 | 检查 #headerAuth | 已挂载 | 认证区域 |
| 6 | `has quick actions grid with 3 cards` | 首页快捷操作 | 首页 | 检查 .quick-action-card | 3 个卡片 | 快捷操作网格 |
| 7 | `has my videos section` | 我的视频区域 | 首页 | 检查 #myVideosSection | 可见，含"我的视频"标题 | 视频区域 |
| 8 | `welcome section visible` | 欢迎区域 | 首页 | 检查 .welcome-section | 可见 | 欢迎信息 |
| 9 | `Homepage no horizontal overflow at 375px` | 首页无横向溢出 | 375px 视口 | 检查 scrollWidth | ≤ 376px | 移动端适配 |
| 10 | `Login no horizontal overflow at 375px` | 登录页无横向溢出 | 375px 视口 | 检查 scrollWidth | ≤ 376px | 移动端适配 |
| 11 | `Upload no horizontal overflow at 375px` | 上传页无横向溢出 | 375px 视口 | 检查 scrollWidth | ≤ 376px | 移动端适配 |
| 12 | `Statistics no horizontal overflow at 375px` | 统计页无横向溢出 | 375px 视口 | 检查 scrollWidth | ≤ 376px | 移动端适配 |

---

## [global.spec.js](../../video/src/test/playwright/tests/global.spec.js)

验证全局配置，包括 CSS 加载、主题切换和响应式 meta 标签。

| # | 测试用例 | 场景 | 前置条件 | 操作 | 期望结果 | 测试目标 |
|---|---------|------|---------|------|---------|---------|
| 1 | `global.css is loaded on login page` | 登录页 CSS | 登录页 | 检查 link 标签 | 恰好 1 个 global.css 引用 | CSS 加载 |
| 2 | `global.css is loaded on upload page` | 上传页 CSS | 上传页（含 mock） | 检查 link 标签 | 恰好 1 个 global.css 引用 | CSS 加载 |
| 3 | `body has css variable --bg-primary defined` | CSS 变量 | 登录页 | 检查计算样式 | --bg-primary 有值 | CSS 变量定义 |
| 4 | `theme toggle button exists on login page` | 主题按钮 | 登录页 | 检查 #theme-toggle | 可见 | 主题按钮存在 |
| 5 | `clicking toggle switches to dark mode` | 暗色模式切换 | 登录页 | 点击 toggle | data-theme = "dark" | 暗色模式 |
| 6 | `theme preference persists in localStorage` | 主题持久化 | 登录页 | 点击 toggle | localStorage.theme 已设置 | 持久化存储 |
| 7 | `viewport meta tag exists` | 响应式 meta | 登录页 | 检查 meta 标签 | 存在 viewport meta 标签 | 响应式配置 |

---

## [login.spec.js](../../video/src/test/playwright/tests/login.spec.js)

验证登录页面的结构和响应式布局。

| # | 测试用例 | 场景 | 前置条件 | 操作 | 期望结果 | 测试目标 |
|---|---------|------|---------|------|---------|---------|
| 1 | `has page title` | 页面标题 | 登录页 | 检查 title | 包含"登录" | 标题正确 |
| 2 | `has centered login card` | 居中登录卡片 | 登录页 | 检查 .card 位置 | 水平居中（±100px 容差） | 卡片居中 |
| 3 | `phone input has proper styling` | 手机号输入框 | 登录页 | 检查 #input_phone | 可见，placeholder="手机号" | 输入框样式 |
| 4 | `verification code input exists` | 验证码输入框 | 登录页 | 检查 #input_verificationCode | 可见 | 验证码字段 |
| 5 | `login button exists and is styled` | 登录按钮 | 登录页 | 检查 #btn_submitVerificationCode | 可见 | 按钮样式 |
| 6 | `error message area exists but hidden initially` | 错误消息区域 | 登录页 | 检查 #errorMessage | 存在但内容为空 | 错误区域初始隐藏 |
| 7 | `card is full width on mobile` | 移动端卡片宽度 | <500px 视口 | 检查卡片宽度 | 卡片宽度/视口宽度 > 85% | 移动端适配 |

---

## [minor-pages.spec.js](../../video/src/test/playwright/tests/minor-pages.spec.js)

验证统计页、YouTube 转存页、保存 Token 页的功能。

| # | 测试用例 | 场景 | 前置条件 | 操作 | 期望结果 | 测试目标 |
|---|---------|------|---------|------|---------|---------|
| 1 | `has chart container` | 图表容器 | 统计页（含 echarts mock） | 检查 #bar-chart | 可见 | 图表存在 |
| 2 | `has query buttons` | 查询按钮 | 统计页 | 检查 #query7Days 和 #query30Days | 可见 | 查询功能 |
| 3 | `chart container is responsive` | 图表响应式 | 统计页 | 检查图表宽度 | 宽度 ≤ 视口宽度 | 响应式 |
| 4 | `has URL input` | YouTube URL 输入 | YouTube 转存页（含 mock token） | 检查 #input_youtubeUrl | 可见 | URL 输入 |
| 5 | `has submit button` | 提交按钮 | YouTube 转存页 | 检查 #btn_submit | 可见 | 提交功能 |
| 6 | `saves token to localStorage` | Token 保存 | 无 | 访问 /save-token.html?token=test-tok-42 | localStorage.token = "test-tok-42"，spinner 可见 | Token 保存流程 |

---

## [upload.spec.js](../../video/src/test/playwright/tests/upload.spec.js)

验证上传页面的结构和元素。

| # | 测试用例 | 场景 | 前置条件 | 操作 | 期望结果 | 测试目标 |
|---|---------|------|---------|------|---------|---------|
| 1 | `has page title` | 页面标题 | 上传页（含 mock） | 检查 title | 包含"上传" | 标题正确 |
| 2 | `has upload zone` | 上传区域 | 上传页 | 检查 .upload-zone | 可见 | 上传区域 |
| 3 | `has title input` | 标题输入 | 上传页 | 检查 #input_title | 可见 | 标题字段 |
| 4 | `has progress bar` | 进度条 | 上传页 | 检查 #progress-bar | 可见 | 进度条 |
| 5 | `has playlist selector` | 播放列表选择器 | 上传页 | 检查 #select_playlist | 可见 | 列表选择 |

---

## [ux.spec.js](../../video/src/test/playwright/tests/ux.spec.js)

验证 UX 组件和用户交互体验，包括 Toast 通知、页面结构、表单校验等。

| # | 测试用例 | 场景 | 前置条件 | 操作 | 期望结果 | 测试目标 |
|---|---------|------|---------|------|---------|---------|
| 1 | `VideoApp.toast function exists` | Toast 函数存在 | 首页 | 检查 window.VideoApp.toast | typeof === 'function' | Toast 可用 |
| 2 | `toast appears with message text` | Toast 显示消息 | 首页 | 调用 toast('测试消息', 'info') | .toast 可见，含消息文字 | Toast 显示 |
| 3 | `toast has correct CSS class for success` | 成功 Toast 样式 | 首页 | toast type='success' | .toast-success 出现 | 成功样式 |
| 4 | `toast has correct CSS class for error` | 错误 Toast 样式 | 首页 | toast type='error' | .toast-error 出现 | 错误样式 |
| 5 | `toast auto-disappears after delay` | Toast 自动消失 | 首页 | 显示 toast | 2.5 秒后消失 | 自动隐藏 |
| 6 | `index.html loads without error` | 首页加载 | 无 | 访问 index.html | HTTP 200 | 页面可用 |
| 7 | `has page header with logo` | 页头 Logo | 首页 | 检查 .page-header .logo | 可见 | Logo 存在 |
| 8 | `has theme toggle` | 主题切换 | 首页 | 检查 #theme-toggle | 可见 | 主题存在 |
| 9 | `has navigation link to upload` | 上传导航 | 首页 | 检查 a[href*="upload"] | 可见/已挂载 | 导航完整 |
| 10 | `has navigation link to statistics` | 统计导航 | 首页 | 检查 a[href*="statistics"] | 可见/已挂载 | 导航完整 |
| 11 | `has navigation link to transfer-youtube` | YouTube 导航 | 首页 | 检查 a[href*="transfer-youtube"] | 可见/已挂载 | 导航完整 |
| 12 | `invalid phone number shows validation error` | 无效手机号 | 登录页（含 axios mock） | 输入 "123" → 发送验证码 | .toast-error 可见 | 前端校验 |
| 13 | `valid phone shows success toast` | 有效手机号 | 登录页（含 axios mock） | 输入 "13800138000" → 发送验证码 | .toast-success 可见 | 成功反馈 |
| 14 | `login API error shows error toast` | 登录 API 错误 | 登录页（含 mock） | 提交错误验证码 | .toast-error 可见（code: -1） | API 错误反馈 |
| 15 | `empty playlist shows message` | 空播放列表 | 上传页（含 mock） | 空播放列表响应 | .empty-state 可见 | 空状态提示 |
| 16 | `copy button shows toast` | 复制按钮 | 上传页（含 mock） | 点击 #btn_copy | .toast-success 可见 | 复制反馈 |
| 17 | `does not use mui.toast` | 不使用 MUI | 观看页 | 检查 MUI 脚本/CSS | 0 个 MUI 引用 | 依赖清洁 |
| 18 | `API error shows toast` | 统计 API 错误 | 统计页（含 mock） | API 返回 code: -1 | .toast-error 可见 | 错误反馈 |
| 19 | `invalid YouTube URL shows validation error` | 无效 YouTube URL | YouTube 转存页（含 mock） | 输入 "not-a-url" → 提交 | .toast-error 可见 | URL 校验 |
| 20 | `submit success shows toast` | YouTube 提交成功 | YouTube 转存页（含 mock） | 输入有效 URL → 提交 | .toast-success 可见 | 成功反馈 |
| 21 | `missing token parameter shows error` | 缺少 Token 参数 | 无 | 访问 /save-token.html 无 token 参数 | .text 显示"缺少登录信息" | 参数缺失提示 |

---

## [player.spec.js](../../video/src/test/playwright/tests/player.spec.js)

验证播放器引擎替换（Aliplayer → Video.js）、资源加载、API 函数和 URL 参数解析。

| # | 测试用例 | 场景 | 前置条件 | 操作 | 期望结果 | 测试目标 |
|---|---------|------|---------|------|---------|---------|
| 1 | `页面加载Video.js而非Aliplayer` | 播放器引擎验证 | mock 环境 | 检查 window.videojs 和 Aliplayer | videojs 存在，Aliplayer 不存在 | 引擎替换验证 |
| 2 | `播放器容器使用video标签` | HTML 元素验证 | mock 环境 | 检查 video#player-con | video 标签存在 | DOM 结构 |
| 3 | `Video.js CSS已加载` | 样式加载验证 | mock 环境 | 检查 link[href*=video-js.css] | CSS 链接存在 | 资源加载 |
| 4 | `没有Aliplayer CSS` | 旧资源清除验证 | mock 环境 | 检查 link[href*=aliplayer] | 不存在 | 清理验证 |
| 5 | `getInitSeekTimeInSeconds函数存在` | 时间跳转函数验证 | mock 环境 | 检查函数类型 | function 类型 | API 验证 |
| 6 | `initKeyboardShortcuts函数存在` | 键盘快捷键函数验证 | mock 环境 | 检查函数类型 | function 类型 | API 验证 |
| 7 | `复制按钮存在且可见` | 复制按钮 UI 验证 | mock 环境 | 检查 #btn_copyCurrentTime | 可见且包含"复制" | UI 验证 |
| 8 | `t参数被getInitSeekTimeInSeconds读取` | t= 参数解析 | mock 环境，URL 含 t=42 | 调用函数 | 返回 42 | URL 参数 |

---

## [watch.spec.js](../../video/src/test/playwright/tests/watch.spec.js)

验证观看页面的结构和响应式布局。

| # | 测试用例 | 场景 | 前置条件 | 操作 | 期望结果 | 测试目标 |
|---|---------|------|---------|------|---------|---------|
| 1 | `has player container` | 播放器容器 | 观看页（含 CDN/API mock） | 检查 #player-con | 存在（count=1） | 播放器存在 |
| 2 | `has video info section` | 视频信息区域 | 观看页 | 检查 .video-info-section | 可见 | 信息区域 |
| 3 | `has video title element` | 视频标题 | 观看页 | 检查 #div_title | 含 "Test Video" | 标题渲染 |
| 4 | `player container has 16:9 aspect ratio wrapper` | 16:9 比例 | 观看页 | 检查 .player-wrapper | count=1 | 宽高比容器 |
| 5 | `playlist is below player on mobile` | 移动端布局 | 375px 视口 | 检查 flex-direction | column | 移动端垂直布局 |
