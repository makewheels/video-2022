# 旧产物校验合成语料

`OutputValidationContractTest`实际执行旧Java OutputValidator，手工装配mock FileService，不启动Spring/Mongo或真实云调用。26组记录保留清单、声明、合成对象大小、失败注入位置、错误文本及精确读取顺序。

Python经普通声明类型、真实适配器和模拟SDK bucket对照；额外断言只对清单读取正文、所有成功取得的结果按顺序关闭。语料不是旧HTTP/真实OSS/FC验证。生成选项`contract.output-validation.dir`必须指向系统临时目录，成功后才复制合成JSON交付。
