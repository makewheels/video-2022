package com.github.makewheels.video2022.video;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.etc.response.Result;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.user.UserService;
import com.github.makewheels.video2022.video.bean.Video;
import com.github.makewheels.video2022.video.bean.VideoDetail;
import com.github.makewheels.video2022.video.bean.VideoSimpleInfoVO;
import com.github.makewheels.video2022.watch.watchinfo.WatchInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("video")
@Slf4j
public class VideoController {
    @Resource
    private UserService userService;
    @Resource
    private VideoService videoService;

    /**
     * 预创建，主要目的是指定上传路径
     */
    @PostMapping("create")
    public Result<JSONObject> create(HttpServletRequest request, @RequestBody JSONObject body) {
        User user = userService.getUserByRequest(request);
        return videoService.create(user, body);
    }

    /**
     * 原始文件上传完成
     */
    @GetMapping("originalFileUploadFinish")
    public Result<Void> originalFileUploadFinish(HttpServletRequest request, @RequestParam String videoId) {
        User user = userService.getUserByRequest(request);
        return videoService.originalFileUploadFinish(user, videoId);
    }

    /**
     * 更新video信息
     */
    @PostMapping("updateInfo")
    public Result<Void> updateInfo(HttpServletRequest request, @RequestBody Video updateVideo) {
        User user = userService.getUserByRequest(request);
        return videoService.updateVideo(user, updateVideo);
    }

    /**
     * 获取播放信息
     * 重要接口，决定用户打开网页到开始播放耗时，所以这个接口追求速度，使用redis缓存
     */
    @GetMapping("getWatchInfo")
    public Result<WatchInfo> getWatchInfo(
            HttpServletRequest request, @RequestParam String watchId,
            @RequestParam String clientId, @RequestParam String sessionId) {
        User user = userService.getUserByRequest(request);
        return videoService.getWatchInfo(user, watchId, clientId, sessionId);
    }

    /**
     * 根据videoId获取视频详情
     *
     * @param request
     * @param videoId
     * @return
     */
    @GetMapping("getVideoDetail")
    public Result<VideoDetail> getVideoDetail(HttpServletRequest request, @RequestParam String videoId) {
        User user = userService.getUserByRequest(request);
        return videoService.getVideoDetail(user, videoId);
    }

    /**
     * 分页获取指定userId视频列表
     *
     * @param userId
     * @param skip
     * @param limit
     * @return
     */
    @GetMapping("getVideoListByUserId")
    public Result<List<VideoSimpleInfoVO>> getVideoList(
            @RequestParam String userId, @RequestParam int skip, @RequestParam int limit) {
        return videoService.getVideoList(userId, skip, limit);
    }

    /**
     * 分页获取我的视频
     */
    @GetMapping("getMyVideoList")
    public Result<List<VideoSimpleInfoVO>> getMyVideoList(
            HttpServletRequest request, @RequestParam int skip, @RequestParam int limit) {
        User user = userService.getUserByRequest(request);
        return videoService.getVideoList(user.getId(), skip, limit);
    }

    /**
     * 根据youtube url获取视频信息
     */
    @GetMapping("getYoutubeVideoInfo")
    public Result<JSONObject> getYoutubeVideoInfo(
            HttpServletRequest request, @RequestParam JSONObject body) {
        User user = userService.getUserByRequest(request);
        return videoService.getYoutubeVideoInfo(body);
    }

    /**
     * 获取m3u8内容，里面是ts列表链接
     */
    @GetMapping("getM3u8Content.m3u8")
    public String getM3u8Content(
            HttpServletRequest request, @RequestParam String videoId,
            @RequestParam String clientId, @RequestParam String sessionId,
            @RequestParam String transcodeId, @RequestParam String resolution) {
        User user = userService.getUserByRequest(request);
        return videoService.getM3u8Content(user, videoId, clientId, sessionId, transcodeId, resolution);
    }

}
