# S10.c.1 凭证与文件完成确认

普通创建后，显式向隔离后端传入`upload_objects`即可安装`GET /file/getUploadCredentials`和`GET /file/uploadFinish`。没有参数时不安装路由；不读取生产凭据、不默认实例化云客户端，共享已有身份/请求日志/异常日志周期。

`FileUploads`按旧顺序检查文件存在、读取所属用户、再读取服务所用文件。文件不存在31、所属不匹配33，均不触达对象存储；缺失fileId按旧绑定失败处理。两入口均要求登录，鉴权先于参数绑定。未新增fileType、deleted、READY或provider枚举限制。

凭证保持bucket/key/endpoint/accessKeyId/secretKey/sessionToken/expiration，追加原文件provider；供应商返回null为34，异常为通用错误。凭证请求不修改文件。`OssUploadObjects`复用显式提供的凭证能力，可与已有`OssAvatars`的STS适配组合，不改变原三小时参数。

完成确认只从远端对象获取实际size/ETag/LastModified，再保存fileStatus READY。size按BSON Long保存；不使用创建时声明size、不刷新updateTime、不写MD5、不更新视频状态或提交任务。远端失败不写库，写库失败不重试；重复通知再次读取远端并保存，保留旧非幂等化行为。Mongo按旧File映射字段整体替换，兼容既有ObjectId，空可选元数据不写入。

`OssUploadObjects.metadata`对显式SDK bucket执行get_object，只读响应头、finally关闭流，不缓冲视频正文。使用本地已安装oss2模型和合成响应验证映射与关闭；没有真实OSS访问验收。

## 验收依据与边界

旧Java `UploadHttpContractTest`生成`serverless/contracts/upload-files/cases.json`，18组真实MVC/隔离Mongo固定完整响应、前后文档、日志与远端调用数。Python同样本实际经ASGI/独享Mongo对照；另验证创建→凭证→两次完成→远端失败保留原READY记录，实际BSON Long、ObjectId兼容、空元数据及写失败不重试。

历史字段缺失与显式null单独对照：旧Spring读取缺失值会应用File构造默认值，凭证请求不将其写回，完成后保存默认值；显式null覆盖构造默认，完成保存时省略null字段。Python按这组实测区别映射，不给历史数据批量回填。

这仅完成S10.c凭证与文件确认子项；客户端直传、真实STS/OSS、完整端上传均未验。`/video/rawFileUploadFinish`仍未装配，必须先完成S12可靠受理/恢复，不能把请求返回前起线程当作可靠任务。MD5/AUTO去重及收费转码未执行。回退本批提交即可撤销显式隔离能力，无生产路由、配置或数据变更。

本地实际Java9passed/0skipped及Checkstyle；Python完整1054passed/2项Windows符号链接权限未跑/0skipped，最终文件域25项定向passed。既有Starlette/oss2 warning不表示未通过；Linux必要CI另按精确提交核对。
