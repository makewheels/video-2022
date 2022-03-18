package com.github.makewheels.video2022.video;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.usermicroservice2022.User;
import com.github.makewheels.video2022.UserServiceClient;
import com.github.makewheels.video2022.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("video")
@Slf4j
public class VideoController {
    @Resource
    private UserServiceClient userServiceClient;
    @Resource
    private VideoService videoService;

    /**
     * 预创建，主要目的是指定上传路径
     */
    @PostMapping("create")
    public Result<JSONObject> create(HttpServletRequest request, @RequestBody JSONObject requestBody) {
        User user = userServiceClient.getUserByRequest(request);
        return videoService.create(user, requestBody);
    }
}
