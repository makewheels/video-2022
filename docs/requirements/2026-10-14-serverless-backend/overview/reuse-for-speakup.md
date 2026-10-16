# video-2022经验用于SpeakUp的核对记录

2026-10-16用户要求先完成video-2022，再把实际可行经验用于SpeakUp，以后无需自管服务器。本页记录源码对照和证据边界，不提前认定两个项目已能无服务器运行，不改变当前生产部署。

SpeakUp依据提交`aa12443fa0e737d73ae37032769e7266cdb25f74`，video-2022依据已合并子阶段及当前验收记录。这里没有访问用户数据或发运营消息。

| 主题 | 已有经验 | SpeakUp源码对照与下一步 |
|---|---|---|
| 公共逻辑与云入口分离 | video business/ports、Mongo/OSS适配、显式装配和架构测试已实际运行 | 可复用分层及测试方法；SpeakUp现有服务规则保留，分别适配运行时和存储，不能直接搬视频领域代码 |
| 数据库初始化 | 视频库已有惰性、有界连接和隔离契约；真实FC跨云网络仍未验 | SpeakUp `server/db/connection.py`启动先ping并创建索引；需拆清发布时索引管理、实例连接池、首连成本和托管数据库迁移，不能每次请求重复启动初始化 |
| 流式响应 | 视频已测多值参数/编码/CORS/普通响应，当前Response为完整正文 | SpeakUp `server/routes/correct.py`是StreamingResponse，两条SSE及reset必须真实云端逐块验证；普通Handler JSON封装测试不能代替SSE证明 |
| 请求内并行 | 应依据完成边界判断可靠性 | SpeakUp `services/corrector.py`标准答案task被await且finally取消未完任务，可保留请求内并行；同文件audit task及`routes/scenarios.py`补题task是未等待的后台工作，需要持久受理/恢复，不能机械把所有create_task作相同处理 |
| 定时通知 | S12进程终止实验已证明写库/投递与未知受理间隙，真实可靠唤醒仍未解决 | SpeakUp main lifespan启动notifier永久sleep循环；事件本身有pending持久记录，未来需可靠安排活跃窗口触发与重试，不让FC依赖常驻循环或空扫，也不双发通知 |
| 前端资源 | 后端冷启动不能阻塞页面骨架；静态托管/路由在视频S23仍待验 | SpeakUp main目前StaticFiles和SPA fallback同进程；需另行托管构建产物并验证深链接、公开分享、API域/CORS和部署版本兼容 |
| 数据与文件 | 现有只读/部分写入、默认值、BSON和对象流关闭契约可以作方法参考 | SpeakUp用户/Attempt/复习/分享/录音均须保留；托管数据库、OSS、权限/签名和备份恢复一起覆盖，API迁FC但仍需维护数据库主机不满足目标 |
| 发布与回退 | S02 Git来源/固定Linux原生依赖/可重现构建已有CI；S03实际隔离发布权限仍受阻 | 可复用产物来源和版本回退方法；SpeakUp当前Python版本/原生包/运行时另核实，不能直接复制视频的依赖锁或仅凭Docker启动成功宣布FC通过 |

目前可复用的是已验证的分层、契约、发布来源和故障调查方法。真正“无需维护服务器”的结论仍需video-2022的FC发布/网络/任务/全端验证，以及SpeakUp自身的流式/语音/托管数据验证后逐项确认。先持续推进video-2022；该页不是要求暂停现有开发或立即重写SpeakUp。
