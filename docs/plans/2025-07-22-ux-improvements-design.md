# UX 改进设计文档

## 目标

中等程度优化前端用户体验：统一 toast 提示、API 错误处理、空状态提示、输入验证，并新增首页。

## 改进范围

### 1. 首页 (index.html)

根路径 `/` 当前返回 404 错误。新增 `index.html` 作为简单导航首页：
- 居中卡片布局，导航链接到上传、统计、搬运 YouTube
- 已登录时显示"欢迎回来"，未登录时显示"请先登录"
- 复用 global.css card 组件

### 2. Toast 组件 (global.js + global.css)

在 `global.js` 中新增 `VideoApp.toast(message, type)` 函数：
- type: `success`(绿) / `error`(红) / `info`(蓝)
- CSS 动画从顶部滑入，2 秒后自动消失
- watch.html 中 `mui.toast` 替换为 `VideoApp.toast`，移除 MUI 依赖

### 3. 各页面 UX 改进

| 页面 | 改进项 |
|------|--------|
| login | 发送验证码成功 toast、手机号格式验证、登录成功 toast、API `.catch()` 错误提示 |
| upload | 修改信息成功 toast、复制链接 toast、空播放列表"暂无播放列表"提示、API `.catch()` |
| watch | `mui.toast` → `VideoApp.toast`，移除 MUI CSS/JS |
| statistics | 无数据时显示"暂无数据"、API 错误 toast |
| transfer-youtube | YouTube URL 格式验证、提交成功/失败 toast |
| save-token | 缺少 token 参数时显示错误提示 |

### 4. 输入验证

- 手机号：11 位数字，以 1 开头
- YouTube URL：包含 `youtube.com` 或 `youtu.be`

## 技术方案

- Toast：纯 CSS + JS，无第三方库
- 所有页面共用 `global.css` + `global.js`
- TDD：先写 Playwright 测试，再实现
- 响应式：手机电脑通用
