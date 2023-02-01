package com.github.makewheels.video2022.file;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.etc.context.Context;
import com.github.makewheels.video2022.etc.context.RequestUtil;
import com.github.makewheels.video2022.etc.response.Result;
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
        Context context = RequestUtil.toDTO(Context.class);
        return fileService.access(context, resolution, fileId, timestamp, nonce, sign);
    }

}
