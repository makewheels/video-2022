# S14.b–d 评论创建/回复、删除与点赞

普通`CommentWrites`/窄写端口、Mongo适配与显式隔离HTTP已实现。`comment_writes=True`安装POST add和GET delete/like，与读取能力独立开关、共用既有身份和同步日志，默认不装配生产。

## 兼容行为

- 鉴权先于绑定。add先检查视频存在，再按Java trim/UTF-16长度判断空内容/2000字；内容错误/父评论不存在/删除无权限等是原直接Result错误，不伪造异常日志。数据库/绑定/空字段异常仍走原通用错误。
- add的JSONObject先由Spring解析为普通容器，再由getString调用Java toString；数组`[1, true, fixture]`和对象`{text=null, n=1}`不能当Fastjson exit的JSON字符串。重复键最后覆盖，数字/布尔与指数转文本按真实样本保留；共用已验证有限Double文本工具，不新增供应商解析器。
- 回复子评论仍指向顶级parentId，被回复者信息来自直接父目标；旧代码允许跨视频目标，不在迁移中新增校验。顺序为父回复数增量→创建评论→视频计数增量，失败保留已发生的写入，不回滚或重放。
- 删除仍允许评论作者或视频uploader，保留空作者/空视频字段与短路判断。顶级评论依次删回复点赞、批删回复、删本条点赞/评论，再扣视频计数；子评论先扣顶层replyCount。源代码对已不存在视频等边界以实际旧样本为准，不增加统一前置存在检查。
- 点赞是toggle，不检查评论/视频存在；旧缺失目标可能仍写like记录。使用现有comment_like唯一索引，不在请求中建索引；冲突时不补计数或自动再跑。
- Mongo生成真实ObjectId，API保持字符串ID；计数沿旧Integer增量、只更新相关字段，时间和null省略保持旧BSON。HEAD在旧GET写路由上同样执行业务，OPTIONS受保护，不支持方法在拦截前通用错误且无请求日志。

## 实际验证

旧真实MVC/独享Mongo43组完整前后video/comment/comment_like、HTTP及日志样本。实际生成ObjectId后仅为比较替换稳定标记，未mock ID分配；仅归一化新建服务端时间，原固定Date保留。覆盖UTF-16边界、泛型容器转文本、回复/跨视频、权限/空字段、级联、toggle和缺失目标。

Python同真实ASGI/独享Mongo比较，并验证父计数后插入失败、插入后视频计数失败、级联后计数失败、实际唯一索引冲突不补计数，以及真实ID/大写ObjectId查询、新增→回复→嵌套回复→toggle→级联→公开读取闭环。另旧Java与Python分别实测HEAD/OPTIONS/方法异常。

最终本机Python3.12.13完整1338passed/0skipped/1既有Starlette warning，Ruff/架构/500行/空白通过；旧Java HTTP2项及既有CommentService26项全部通过，Maven validate通过。依赖/构建/Mongo任务临时目录和独享进程均清理；没有生产、真实云或客户端切流。

六个评论HTTP主路径已有隔离实现，S14仍需完整Web/移动端、真实FC、额外历史BSON/JSON类型和并发/资源矩阵。没有将旧多写步骤改为事务或新增幂等协议，不把故障残留当成已解决的可靠写入保证。
