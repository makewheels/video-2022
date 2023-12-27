package com.github.makewheels.video2022.video;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.etc.check.CheckService;
import com.github.makewheels.video2022.etc.system.response.Result;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.video.bean.dto.CreateVideoDTO;
import com.github.makewheels.video2022.video.bean.dto.UpdateVideoInfoDTO;
import com.github.makewheels.video2022.video.bean.dto.UpdateWatchSettingsDTO;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.bean.vo.VideoVO;
import com.github.makewheels.video2022.video.service.VideoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("video")
@Slf4j
public class VideoController {
    @Resource
    private VideoService videoService;
    @Resource
    private CheckService checkService;

    /**
     * 预创建视频，主要是指定上传路径
     */
    @PostMapping("create")
    public Result<JSONObject> create(@RequestBody CreateVideoDTO createVideoDTO) {
        checkService.checkCreateVideoDTO(createVideoDTO);
        JSONObject response = videoService.create(createVideoDTO);
        return Result.ok(response, "视频已创建");
    }

    /**
     * 原始文件上传完成
     */
    @GetMapping("rawFileUploadFinish")
    public Result<Void> rawFileUploadFinish(@RequestParam String videoId) {
        checkService.checkVideoExist(videoId);
        videoService.rawFileUploadFinish(videoId);
        return Result.ok();
    }

    /**
     * 更新video信息
     */
    @PostMapping("updateInfo")
    public Result<Video> updateInfo(@RequestBody UpdateVideoInfoDTO updateVideoInfoDTO) {
        checkService.checkVideoExist(updateVideoInfoDTO.getId());
        checkService.checkVideoBelongsToUser(updateVideoInfoDTO.getId(), UserHolder.getUserId());
        Video video = videoService.updateVideo(updateVideoInfoDTO);
        return Result.ok(video);
    }

    /**
     * 根据videoId获取视频详情
     */
    @GetMapping("getVideoDetail")
    public Result<VideoVO> getVideoDetail(@RequestParam String videoId) {
        checkService.checkVideoExist(videoId);
        VideoVO videoVO = videoService.getVideoDetail(videoId);
        return Result.ok(videoVO);
    }

    /**
     * 分页获取我的视频
     */
    @GetMapping("getMyVideoList")
    public Result<List<VideoVO>> getMyVideoList(@RequestParam int skip, @RequestParam int limit) {
        return videoService.getMyVideoList(skip, limit);
    }

    /**
     * 获取原始文件下载地址
     */
    @GetMapping("getRawFileDownloadUrl")
    public Result<JSONObject> getRawFileDownloadUrl(@RequestParam String videoId) {
        String url = videoService.getRawFileDownloadUrl(videoId);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("url", url);
        return Result.ok(jsonObject);
    }

    /**
     * 更新播放设置
     */
    @PostMapping("updateWatchSettings")
    public Result<Void> updateWatchSettings(@RequestBody UpdateWatchSettingsDTO updateWatchSettingsDTO) {
        checkService.checkVideoExist(updateWatchSettingsDTO.getVideoId());
        videoService.updateWatchSettings(updateWatchSettingsDTO);
        return Result.ok();
    }
}
