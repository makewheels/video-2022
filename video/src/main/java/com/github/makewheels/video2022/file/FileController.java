package com.github.makewheels.video2022.file;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.model.OSSObject;
import com.github.makewheels.video2022.etc.check.CheckService;
import com.github.makewheels.video2022.system.context.RequestUtil;
import com.github.makewheels.video2022.system.response.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("file")
public class FileController {
    @Resource
    private FileService fileService;
    @Resource
    private CheckService checkService;

    /**
     * 获取对象存储文件信息，后台调用接口
     */
    @GetMapping("getOssObjectMetadataByKey")
    public Result<OSSObject> getOssObjectMetadataByKey(@RequestParam String key) {
        OSSObject ossObject = fileService.getObject(key);
        return Result.ok(ossObject);
    }

    /**
     * 获取上传凭证
     */
    @GetMapping("getUploadCredentials")
    public Result<JSONObject> getUploadCredentials(@RequestParam String fileId) {
        checkService.checkFileBelongsToUserHolder(fileId);
        JSONObject uploadCredentials = fileService.getUploadCredentials(fileId);
        return Result.ok(uploadCredentials);
    }

    /**
     * 当前文件上传完成时
     */
    @GetMapping("uploadFinish")
    public Result<Void> uploadFinish(@RequestParam String fileId) {
        checkService.checkFileBelongsToUserHolder(fileId);
        fileService.uploadFinish(fileId);
        return Result.ok();
    }

    /**
     * 访问文件
     * TODO 访问文件应该区分类型，是ts，还是cover，会进入不同的repository查询，
     * 也能把封面权限改为private，所有文件访问都从应用服务器转发到阿里云OSS
     */
    @GetMapping("access")
    public Result<Void> access(
            @RequestParam String resolution, @RequestParam String fileId, @RequestParam String timestamp,
            @RequestParam String nonce, @RequestParam String sign) {
        fileService.access(RequestUtil.getContext(), resolution, fileId, timestamp, nonce, sign);
        return Result.ok();
    }

}
