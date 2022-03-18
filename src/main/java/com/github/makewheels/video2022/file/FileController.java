package com.github.makewheels.video2022.file;

import com.github.makewheels.usermicroservice2022.User;
import com.github.makewheels.video2022.UserServiceClient;
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

    @GetMapping("getUploadCredential")
    public void getUploadCredential(HttpServletRequest request, @RequestParam String fileId) {
        User user = userServiceClient.getUserByRequest(request);
        fileService.getUploadCredential(user, fileId);
    }

    @GetMapping("uploadFinish")
    public void uploadFinish(HttpServletRequest request) {
        User user = userServiceClient.getUserByRequest(request);
    }

}
