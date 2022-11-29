package com.github.makewheels.video2022.file;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.etc.response.Result;
import com.github.makewheels.video2022.user.User;
import com.github.makewheels.video2022.user.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("file")
public class FileController {
    @Resource
    private UserService userService;
    @Resource
    private FileService fileService;

    @GetMapping("getUploadCredentials")
    public Result<JSONObject> getUploadCredentials(HttpServletRequest request, @RequestParam String fileId) {
        User user = userService.getUserByRequest(request);
        return fileService.getUploadCredentials(user, fileId);
    }

    @GetMapping("uploadFinish")
    public Result<Void> uploadFinish(HttpServletRequest request, @RequestParam String fileId) {
        User user = userService.getUserByRequest(request);
        return fileService.uploadFinish(user, fileId);
    }

    @GetMapping("access")
    public Result<Void> access(
            HttpServletRequest request, HttpServletResponse response,
            @RequestParam String videoId, @RequestParam String clientId, @RequestParam String sessionId,
            @RequestParam String resolution, @RequestParam String fileId, @RequestParam String timestamp,
            @RequestParam String nonce, @RequestParam String sign) {

        User user = userService.getUserByRequest(request);
        return fileService.access(request, response, videoId, clientId, sessionId, resolution, fileId,
                timestamp, nonce, sign);
    }

}
