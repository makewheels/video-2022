# 浏览器端到端测试（Python + Playwright）

> 文件名仍保留为 `10-Playwright前端测试.md`，但当前真实测试目录已经迁移到 `test/browser/`，不再使用旧的 `video/src/test/playwright/` 路径。

浏览器端到端测试通过 `pytest + Playwright` 运行，主要验证页面可访问性、鉴权跳转、基础交互和关键页面骨架。

- **测试目录**：`test/browser/`
- **测试文件数**：12
- **测试用例数**：80

## 运行方式

```bash
# 运行全部浏览器 E2E
python3 -m pytest test/browser

# 运行单个测试文件
python3 -m pytest test/browser/test_login.py
```

## 当前测试套件

| 文件 | 说明 |
|------|------|
| `test_auth_session.py` | 鉴权过期、受保护页面重定向、target 参数保留 |
| `test_channel.py` | 频道页可访问性与公共可见性 |
| `test_edit_page.py` | 编辑页鉴权与基本结构 |
| `test_home_page.py` | 首页加载、空状态、基础布局 |
| `test_login.py` | 登录页表单结构、手机号输入、主题按钮 |
| `test_minor_pages.py` | 统计页、YouTube 页面等次级页面骨架 |
| `test_my_videos.py` | 我的视频列表、搜索框、鉴权 |
| `test_navigation.py` | 顶部导航、移动菜单、链接跳转 |
| `test_search.py` | 搜索栏与搜索入口 |
| `test_settings.py` | 设置页表单、鉴权、字符计数 |
| `test_upload.py` | 上传页 drop zone、隐藏文件输入、鉴权 |
| `test_watch.py` | 观看页布局、标题、播放器容器、基础元信息 |

## 当前覆盖重点

- 未登录访问受保护页面会被重定向到 `/login`
- 登录页、上传页、设置页、我的视频页等核心页面可以稳定加载
- 导航、搜索、频道页和观看页的关键结构存在
- 统计页 / YouTube 页等次级页面不会出现明显的路由或挂载错误

## 当前未覆盖的高价值场景

- 真实播放链路：`/playback/start -> /playback/heartbeat -> /playback/exit`
- 复杂上传流程（文件选择、上传进度、上传完成后的状态切换）
- 开发者控制台核心流程（应用创建、Webhook 管理、统计页数据变化）

这些场景目前主要依赖后端集成测试、Web 单元测试和人工验证。
