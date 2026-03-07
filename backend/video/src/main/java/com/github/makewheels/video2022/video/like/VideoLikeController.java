package com.github.makewheels.video2022.video.like;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.etc.check.CheckService;
import com.github.makewheels.video2022.system.response.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

@RestController
@RequestMapping("videoLike")
public class VideoLikeController {
    @Resource
    private VideoLikeService videoLikeService;
    @Resource
    private CheckService checkService;

    @GetMapping("like")
    public Result<Void> like(@RequestParam String videoId) {
        checkService.checkVideoExist(videoId);
        return videoLikeService.react(videoId, LikeType.LIKE);
    }

    @GetMapping("dislike")
    public Result<Void> dislike(@RequestParam String videoId) {
        checkService.checkVideoExist(videoId);
        return videoLikeService.react(videoId, LikeType.DISLIKE);
    }

    @GetMapping("getStatus")
    public Result<JSONObject> getStatus(@RequestParam String videoId) {
        checkService.checkVideoExist(videoId);
        return videoLikeService.getLikeStatus(videoId);
    }
}
