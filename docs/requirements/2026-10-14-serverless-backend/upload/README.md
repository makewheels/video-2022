# 原片上传与AUTO去重

[返回迁移总览](../README.md) · [执行清单](../overview/remaining-work.md)

- [S10.a 上传HTTP契约](upload-contract.md)：创建、凭证、文件完成和视频完成的原行为及任务边界。
- [S10.a.2 / S10.b.1 普通创建](ordinary-creation.md)：旧完整BSON/绑定样本、Python普通创建与显式隔离HTTP，已合并[#217](https://github.com/makewheels/video-2022/pull/217)。
- [S10.c.1 凭证与文件完成确认](file-completion.md)：显式对象存储能力、完整旧HTTP/Mongo对照；原片可靠受理和MD5另做。
- [S10.b.2 状态轮询](video-status.md)：旧uploader权限、缺失/null及精确READY语义与只读HTTP对照。
- [S11.a.1 / S11.c.1 LOCAL封面登记](local-covers.md)：旧HTTP/完整BSON、普通能力和显式隔离入口；LOCAL转码/侧车与真实FC仍待做。
- [S11.a.2 / S11.c.2 LOCAL档位创建](local-transcodes.md)：两档预建、源媒体信息保留、旧HTTP/完整BSON；完成扫描/就绪链路另做。
- [S11.b.1 只读产物校验](output-validation.md)：TS/fMP4/init、manifest/对象对照与SDK流关闭；尚未装配完成写入。
- 没有实际FC或完整客户端上传验收，不能把普通创建能力称作整体上传迁移完成。
