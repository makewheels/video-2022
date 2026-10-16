# LOCAL档位创建旧HTTP/Mongo契约

来源：`LocalTranscodeHttpContractTest`实际旧MVC/独享Mongo生成30组合成记录。包含请求原文、HTTP状态/类型/正文、排序后的video/file/transcode完整前后BSON、日志数和合成凭证调用数；没有真实媒体或云请求。

顶层createTime/updateTime/finishTime在断言实际Date后替换为`<server-date>`；原视频日期固定2024-01-15，目录年月不归一化。保留尺寸、帧率、HDR/色彩与完整分辨率文档，不只对成功响应。

生成选项`-Dcontract.local-transcode.output.dir`指向系统临时目录；本机JDK21生成并通过validate，成功后复制合成JSON为持久验收样本。Python使用真实ASGI/独享Mongo对照；真实STS/FC及客户端端到端不在该证据内。
