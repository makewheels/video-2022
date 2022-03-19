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

    /**
     * 原始文件上传完成
     */
    @GetMapping("originalFileUploadFinish")
    public Result<Void> create(HttpServletRequest request, @RequestParam String videoId) {
        User user = userServiceClient.getUserByRequest(request);
        return videoService.originalFileUploadFinish(user, videoId);
    }

    /**
     * 更新video信息
     */
    @PostMapping("updateInfo")
    public Result<Void> updateInfo(HttpServletRequest request, @RequestBody Video updateVideo) {
        User user = userServiceClient.getUserByRequest(request);
        return videoService.updateVideo(user, updateVideo);
    }

    /**
     * 获取播放信息
     * 重要接口，决定用户打开网页到开始播放耗时，所以这个接口追求速度
     */
    @GetMapping("getPlayInfo")
    public Result<Void> getPlayInfo(HttpServletRequest request, @RequestParam String videoId) {
        // TODO
        User user = userServiceClient.getUserByRequest(request);
        return videoService.getPlayInfo(user, videoId);
    }
}
