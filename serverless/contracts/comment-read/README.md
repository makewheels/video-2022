# 旧评论读取HTTP/Mongo契约

`CommentReadHttpContractTest`实际旧MVC/独享Mongo生成48组合成样本，包含入口/参数、完整评论与视频输入、完整HTTP响应、请求/异常日志。前后实际业务集合完全相等，日期为固定Date，响应保留真实UTC编码和null字段，无时间归一化或用户数据。

Python同真实ASGI/Mongo比较。生成参数`contract.comment-read.output.dir`使用任务系统临时目录，通过后才复制合成JSON；没有真实云、写操作或全端验收。
