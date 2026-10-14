# 请求生命周期 Java HTTP 黄金响应

unauthorized：受保护getMyProfile无token，HTTP401/application/json;charset=UTF-8，body没有data。binding-error：getChannel缺必需userId，由旧全局异常处理返回HTTP200/application/json、code1/未知错误/data null。

两份由UserReadGoldenHttpContractTest真实Spring管线+隔离Mongo生成，旧运行代码基于master708ac2b8，扩展测试和本目录一同提交。测试11passed/Checkstyle，导出路径受限系统任务临时video-user-read-*父目录下goldens；WriteMapNullValue且重解析验证。持久文件仅格式化，不改值。固定合成上下文，无生产身份/日志。

Python对照全部status/contentType/body，无新增忽略项。既有user-read/profile-read夹具值未变；完整Python235passed。不能替代完整路由、域异常、日志适配或FC验证。
