# S14.a.1 评论分页、回复与计数

普通`Comments`/读取端口、Mongo适配和显式隔离HTTP已实现。`public_comment_reads=True`安装`GET /comment/getByVideoId`、`GET /comment/getReplies`和`GET /comment/getCount`，共享既有请求身份/日志；默认不安装，不切生产。

- 保留旧公开读取语义，匿名或无效token不新增401；视频存在检查用于分页/计数，回复仅按parentId查询，不添加视频可见性或父评论存在校验。
- 顶级评论匹配parentId缺失/null，空字符串仍作为回复键。排序只对精确likeCount使用热度+时间，其余按时间；回复按时间升序。计数统计所有评论，不取Video.commentCount缓存。
- 分页先count后find，保留Java int乘法溢出、pageSize上限50、0/负limit不限条数、负skip忽略和totalPages饱和/负值；复用已验证Spring整数绑定，不新增正数规则。
- 评论字段保持null，日期是毫秒精度UTC字符串；读取元数据直接来自旧Comment，不增加用户查询/头像签名或修改返回内容。
- 只读业务集合，仍按原语义写请求/异常日志；不存在视频仍code22，绑定错误走通用错误。

真实旧MVC/独享Mongo48组完整响应、日志与读取前后不变对照，覆盖三个入口、空/缺失/null父键、PRIVATE视频匿名读取、坏token、排序、十六进制/错误/溢出分页及0/负/超大参数。Python同真实ASGI/Mongo比较，并验证ObjectId、共享生命周期/开关和count失败不继续分页。

本机Python3.12.13完整1285passed/0skipped/1既有Starlette warning，Ruff/架构/500行/空白通过；JDK21旧Java HTTP契约1passed/0skipped，validate通过。环境/构建/独享Mongo任务临时目录清理，无真实云或生产变化。

S14尚缺创建/回复、删除/级联/计数、点赞切换和完整客户端回归；本批不包含写入权限、重复/部分失败或全历史BSON类型证明。LOCAL完成路径的文件/存储/Webhook依赖继续保留，推进独立评论域不等于跳过这些验收。
