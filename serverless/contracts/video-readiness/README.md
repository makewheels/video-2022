# 旧全档就绪状态语料

真实`VideoReadinessContractTest`调用旧`TranscodeCallbackService.updateVideoStatus`及真实独享Mongo，固定45组合成provider/status/currentProvider、空/缺失/混合引用与READY状态。

完整前后Video保留固定updateTime Date（序列化毫秒12345）和未知字段，比较是否只更新status；记录旧方法内存状态和完整档位输入。Python同真实Mongo对照，未触发文件读取、Webhook或其他就绪副作用，不代替完成HTTP/云验收。

生成选项`contract.readiness.output.dir`使用系统临时目录；成功后才复制合成JSON作为持久交付。
