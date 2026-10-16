# LOCAL封面旧HTTP/Mongo契约

来源：`LocalCoverHttpContractTest`执行旧Java真实MVC和隔离Mongo。25组记录包含操作、场景、正文、前后video/file/cover BSON、HTTP状态/类型/正文、请求/异常日志和合成凭证调用数。

仅归一化顶层createTime/updateTime/finishTime为`<server-date>`，归一化前断言实际Date；视频创建时间固定为2024-01-15，保证路径年月可对照。ID及STS值为合成固定值，凭证、MD5、MPS和云函数没有真实调用。动态JSON中的null不归一化，必须保留。

Python以同一固定日期和假凭证能力，经真实ASGI/独享Mongo比较完整记录。生成时通过`-Dcontract.local-cover.output.dir`指定系统临时目录，验证成功后才将合成样本作为持久契约交付。
