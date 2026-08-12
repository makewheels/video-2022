# YouTube 风格 UI 改版设计

## 目标
将 video-2022 的 Web、Android、iOS 三端 UI 向 YouTube 看齐，同时新增公开视频流 API。

## 改版内容

### 后端
- 新增 `GET /video/getPublicVideoList` 公开视频流接口
- 返回视频列表 + 上传者信息

### Web 前端
- NavBar: 搜索栏、用户头像、上传按钮
- Home: 公开视频流，响应式网格 (4/3/2/1列)
- VideoCard: 大缩略图 + 用户头像 + YouTube 风格元数据
- WatchPage: 右侧推荐视频栏、频道信息、可展开描述
- CommentSection: 头像 + 用户名排版

### Android
- HomeScreen: 公开视频流，单列大卡片
- VideoCard: 大缩略图 + 头像 + 元数据
- WatchScreen: 频道信息、推荐视频

### iOS
- 同 Android 改版内容

### 测试
- 现有自动化测试保持通过
- 新增/修改组件更新对应测试
