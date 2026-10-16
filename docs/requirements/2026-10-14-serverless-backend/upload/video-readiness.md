# S11.d.1 全档就绪状态普通能力

迁入旧`TranscodeCallbackService.updateVideoStatus`的普通状态计算与现有Mongo窄写适配；尚未装配完成HTTP或就绪副作用。

- currentProvider非null时覆盖provider，空字符串也覆盖；MPS仅TranscodeSuccess算成功，LOCAL/旧云函数/自建CPU/GPU仅FINISHED算成功，未知提供方不算成功。失败统计仍只看FAILED，不把MPS TranscodeFail自动改成该常量。
- 无查询结果不更新；全部成功READY、全部FAILED则TRANSCODE_FAILED、有成功则TRANSCODING_PARTLY_COMPLETE，其余TRANSCODING。已有READY及状态相同均不写。
- 按数据库实际查回的档位计数，保留旧`$in`去重和缺失引用忽略语义，不增加“引用数必须匹配”的新校验。
- 只`$set status`，保留updateTime和其他/未知字段，不覆盖整个Video、不upsert、不重试。返回内存状态与旧方法一致；视频在写入前消失时，不创建替代记录。
- READY保护依据调用者已读快照，未新增事务/CAS或并发保证，不能把它宣称为所有竞态下的全局单调性。

真实旧Java/独享Mongo45组固定所有提供方、状态、覆盖值、空/缺失/混合引用与READY保护，完整前后文档保留固定Date和未知字段。Python同实际Mongo逐项比较，并验证ObjectId、重复/缺失ID、写失败不重放及无upsert。

本机Python3.12.13完整1234passed/0skipped/1既有Starlette warning，Ruff/架构/500行/空白通过；Java旧状态契约1passed/0skipped、Maven validate通过。构建/依赖/Mongo数据与独享进程均系统临时目录清理。没有真实云、HTTP完成或Webhook验收。

下一批把校验/目录/分片登记串入完成路径，先证明文件登记成功再推进状态；存储类型与Webhook仍需相应普通能力及可靠执行，不用空副作用掩盖缺口。
