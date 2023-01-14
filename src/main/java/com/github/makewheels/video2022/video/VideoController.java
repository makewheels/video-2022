package com.github.makewheels.video2022.video;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.etc.response.Result;
import com.github.makewheels.video2022.video.bean.CreateVideoDTO;
import com.github.makewheels.video2022.video.bean.Video;
import com.github.makewheels.video2022.video.bean.VideoDetailVO;
import com.github.makewheels.video2022.video.bean.VideoSimpleInfoVO;
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

    /**
     * 预创建视频，主要是指定上传路径
     */
    @PostMapping("create")
    public Result<JSONObject> create(@RequestBody CreateVideoDTO createVideoDTO) {
        return videoService.create(createVideoDTO);
    }

    /**
     * 原始文件上传完成
     */
    @GetMapping("originalFileUploadFinish")
    public Result<Void> originalFileUploadFinish(@RequestParam String videoId) {
        return videoService.originalFileUploadFinish(videoId);
    }

    /**
     * 更新video信息
     */
    @PostMapping("updateInfo")
    public Result<Void> updateInfo(@RequestBody Video updateVideo) {
        return videoService.updateVideo(updateVideo);
    }

    /**
     * 根据videoId获取视频详情
     */
    @GetMapping("getVideoDetail")
    public Result<VideoDetailVO> getVideoDetail(@RequestParam String videoId) {
        return videoService.getVideoDetail(videoId);
    }

    /**
     * 分页获取我的视频
     */
    @GetMapping("getMyVideoList")
    public Result<List<VideoSimpleInfoVO>> getMyVideoList(@RequestParam int skip, @RequestParam int limit) {
        return videoService.getMyVideoList(skip, limit);
    }

    /**
     * 获取原始文件下载地址
     */
    @GetMapping("getOriginalFileDownloadUrl")
    public Result<JSONObject> getMyVideoList(@RequestParam String videoId) {
        return videoService.getOriginalFileDownloadUrl(videoId);
    }
}
