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
        checkService.checkFileExist(fileId);
        checkService.checkUserHolderExist();
        return fileService.getUploadCredentials(fileId);
    }

    /**
     * 当前文件上传完成时
     */
    @GetMapping("uploadFinish")
    public Result<Void> uploadFinish(@RequestParam String fileId) {
        return fileService.uploadFinish(fileId);
    }

    /**
     * 访问文件
     */
    @GetMapping("access")
    public Result<Void> access(
            @RequestParam String resolution, @RequestParam String fileId, @RequestParam String timestamp,
            @RequestParam String nonce, @RequestParam String sign) {
        return fileService.access(RequestUtil.getContext(), resolution, fileId, timestamp, nonce, sign);
    }

}
