# Java HTTP 资料更新黄金响应

success（15个非BMP emoji昵称及空白简介）、nickname-long（16个emoji）、bio-long（201个中文字符）三份固定响应，由UserReadGoldenHttpContractTest实际请求旧Spring/隔离Mongo生成。运行代码基于masteraf2c068f，扩展测试及本目录一同提交；无生产身份或数据。

Java黄金HTTP14项及Mongo更新基线6项共20passed/Checkstyle。导出器限系统任务临时video-user-read-*父目录下goldens，保留null且重解析校验；持久文件仅格式化。既有11份user/profile/request-cycle夹具值不改。Python全部status/contentType/body对照，完整247passed；不代表实际HTTP路由或FC已装配。
