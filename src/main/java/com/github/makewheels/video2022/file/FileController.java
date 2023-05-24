package com.github.makewheels.video2022.file;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.github.makewheels.video2022.system.context.RequestUtil;
import com.github.makewheels.video2022.system.response.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("file")
public class FileController {
    @Resource
    private FileService fileService;

    @GetMapping("getOssObjectMetadataByKey")
    public Result<Map<String, String>> getOssObjectMetadataByKey(@RequestParam String key) {
        OSSObject object = fileService.getObject(key);
        ObjectMetadata objectMetadata = object.getObjectMetadata();
        String contentMD5 = objectMetadata.getContentMD5();
        Map<String, String> map = new HashMap<>();
        map.put("contentMD5", contentMD5);
        return Result.ok(map);
    }

    @GetMapping("getUploadCredentials")
    public Result<JSONObject> getUploadCredentials(@RequestParam String fileId) {
        return fileService.getUploadCredentials(fileId);
    }

    @GetMapping("uploadFinish")
    public Result<Void> uploadFinish(@RequestParam String fileId) {
        return fileService.uploadFinish(fileId);
    }

    @GetMapping("access")
    public Result<Void> access(
            @RequestParam String resolution, @RequestParam String fileId, @RequestParam String timestamp,
            @RequestParam String nonce, @RequestParam String sign) {
        return fileService.access(RequestUtil.getContext(), resolution, fileId, timestamp, nonce, sign);
    }

}
