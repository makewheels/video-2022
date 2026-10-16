# S11.a.2 / S11.c.2 LOCAL转码档位创建

显式隔离后端通过`local_transcode_registration=True`安装`POST /transcode/local/createTranscode`，必须提供对象凭证能力。生产入口不变，完成通知尚未安装：不能在OSS扫描和就绪链路迁入前用空实现返回成功。

## 保留的旧行为

- 用户token先于正文绑定；重复检查视频存在，再检查用户和uploader，最后重新读取视频。ownerId不替代上传者；只有精确LOCAL模式接受创建。
- durationMs要求非null，旧0/负值和尺寸没有新增正数校验。源片mediaInfo仅在duration缺失时填写；已有duration=0也视为已经填写，多档调用不覆盖。精确frameRateFraction优先，旧Double帧率继续按旧文本方式保存。
- 档位使用显示宽高（分别可选覆盖源宽高）的短边：不足1080仅720p，否则720p与1080p。低于720不放大，按Java单精度缩放/Math.round和向零偶数对齐保留原结果，不用Python双精度/银行家round直接替代。
- 所有适用档位一次建立，固定`tr_local_{videoId}_{resolution}`和`LOCAL_TRANSCODE`提供方；先保存各档，再保存video.transcodeIds。已存在档位原样复用，包括原状态和对象路径，不重置已完成档位。
- 既有READY视频保持READY；其他视频进入TRANSCODING。源文件缺失时旧getKeyByFileId返回null，不新增文件存在错误。
- STS在全部登记完成后获取；失败/返回null保留先前文档，null仍code34。保存中途失败不回滚、不隐式重试，已有部分档位可由后续显式调用查回。
- 不调用MD5/探测/云转码/截帧，不分配平台任务或开始后台线程，不读取上传媒体正文。

## 实际验收

`LocalTranscodeHttpContractTest`以真实旧MVC/独享Mongo生成30组完整HTTP、video/file/transcode前后BSON、日志与合成STS计数。包含横竖屏、显示尺寸、奇数/零/负尺寸、单精度边界、两档、已有档位、已有时长、READY、精确帧率/HDR/色彩/音轨、缺失原片、权限、绑定及STS失败。

Python同一组样本经真实ASGI/独享Mongo逐项比较；额外验证BSON Long时长、显式开关/共用生命周期、三个保存失败位置、多档重复调用不覆盖媒体和已完成档位。未安装的finish路径返回404，不假装迁移完成。

本机Python3.12.13完整1157passed/0skipped/1既有Starlette弃用warning，Ruff/架构/500行/空白通过；JDK21旧Java HTTP契约1passed/0skipped、Maven validate通过。首次对照发现提供方常量误写为LOCAL，已按旧BSON修正为LOCAL_TRANSCODE后完整复跑通过。临时构建、依赖与独享Mongo均清理，无生产/收费云调用。

完整Double文本/所有历史BSON类型、并发重复创建、ObjectId扩展矩阵、真实STS/CLI与FC仍待补；本批不是任意数据兼容或完整LOCAL端到端证明。下一批迁目录/清单/媒体文件读取与finish登记、全档就绪和部分失败恢复，保持历史TS及fMP4/init/audio对象。
