package com.github.makewheels.video2022.file;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.usermicroservice2022.User;
import com.github.makewheels.video2022.UserServiceClient;
import com.github.makewheels.video2022.response.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("file")
public class FileController {
    @Resource
    private UserServiceClient userServiceClient;
    @Resource
    private FileService fileService;

    @GetMapping("getUploadUrl")
    public Result<JSONObject> getUploadUrl(HttpServletRequest request, @RequestParam String fileId) {
        User user = userServiceClient.getUserByRequest(request);
        return fileService.getUploadUrl(user, fileId);
    }

    @GetMapping("uploadFinish")
    public Result<Void> uploadFinish(HttpServletRequest request, @RequestParam String fileId) {
        User user = userServiceClient.getUserByRequest(request);
        return fileService.uploadFinish(user, fileId);
    }

}
