# 固定用户读取 HTTP 黄金夹具

来源：Git 的 UserReadGoldenHttpContractTest，Java业务基线 fbb8502a，2026-10-15。只含固定合成ID/手机号/旧token/URL/日期，无实际个人资料、云密钥或签名。status/contentType/body保留实际响应及所有null字段；JSON格式整理不改变值/字段/类型。

四个样本：token-found、token-missing、id-found、id-missing。用真实隔离Mongo和完整Java HTTP生成，Python普通HTTP值与其离线对照。它们不覆盖缺参数、请求上下文/日志、Mongo旧文档转换或实际FC网络。

重建：系统临时任务目录名以video-user-read-开头，创建goldens子目录；用本任务临时mongod及独立端口运行Java测试，-Duser.read.fixture.dir指向goldens。只有四个固定样本是持久测试产物，源码副本/target/dbpath/log/临时导出均清理。CI默认不导出文件，但执行相同HTTP断言。
