package com.github.makewheels.video2022.video;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.usermicroservice2022.user.User;
import com.github.makewheels.video2022.response.Result;
import com.github.makewheels.video2022.user.UserServiceClient;
import com.github.makewheels.video2022.video.bean.Video;
import com.github.makewheels.video2022.video.bean.VideoDetail;
import com.github.makewheels.video2022.video.bean.VideoSimpleInfo;
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
    private UserServiceClient userServiceClient;
    @Resource
    private VideoService videoService;

    /**
     * 预创建，主要目的是指定上传路径
     */
    @PostMapping("create")
    public Result<JSONObject> create(HttpServletRequest request, @RequestBody JSONObject body) {
        User user = userServiceClient.getUserByRequest(request);
        return videoService.create(user, body);
    }

    /**
     * 原始文件上传完成
     */
    @GetMapping("originalFileUploadFinish")
    public Result<Void> originalFileUploadFinish(HttpServletRequest request, @RequestParam String videoId) {
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
     * 重要接口，决定用户打开网页到开始播放耗时，所以这个接口追求速度，使用redis缓存
     */
    @GetMapping("getWatchInfo")
    public Result<WatchInfo> getWatchInfo(HttpServletRequest request, @RequestParam String watchId) {
        User user = userServiceClient.getUserByRequest(request);
        return videoService.getWatchInfo(user, watchId);
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
        User user = userServiceClient.getUserByRequest(request);
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
    public Result<List<VideoSimpleInfo>> getVideoList(
            @RequestParam String userId, @RequestParam int skip, @RequestParam int limit) {
        return videoService.getVideoList(userId, skip, limit);
    }

    /**
     * 分页获取我的视频
     */
    @GetMapping("getMyVideoList")
    public Result<List<VideoSimpleInfo>> getVideoList(
            HttpServletRequest request, @RequestParam int skip, @RequestParam int limit) {
        User user = userServiceClient.getUserByRequest(request);
        return videoService.getVideoList(user.getId(), skip, limit);
    }

    /**
     * 增加观看记录
     *
     * @param request
     * @param videoId
     * @param clientId 前端先向用户微服务获取clientId，再调此接口
     * @return
     */
    @GetMapping("addWatchLog")
    public Result<Void> addWatchLog(
            HttpServletRequest request, @RequestParam String videoId,
            @RequestParam String clientId, @RequestParam String sessionId) {
        User user = userServiceClient.getUserByRequest(request);
        return videoService.addWatchLog(request, user, clientId, sessionId, videoId);
    }

    /**
     * 根据youtube url获取视频信息
     */
    @GetMapping("getYoutubeVideoInfo")
    public Result<JSONObject> getYoutubeVideoInfo(
            HttpServletRequest request, @RequestParam JSONObject body) {
        User user = userServiceClient.getUserByRequest(request);
        return videoService.getYoutubeVideoInfo(body);
    }

}
