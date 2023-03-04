package com.github.makewheels.video2022.video;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.etc.response.Result;
import com.github.makewheels.video2022.video.bean.dto.CreateVideoDTO;
import com.github.makewheels.video2022.video.bean.video.Video;
import com.github.makewheels.video2022.video.bean.vo.VideoVO;
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
        JSONObject response = videoService.create(createVideoDTO);
        return Result.ok(response, "视频已创建");
    }

    /**
     * 原始文件上传完成
     */
    @GetMapping("originalFileUploadFinish")
    public Result<Void> originalFileUploadFinish(@RequestParam String videoId) {
        videoService.originalFileUploadFinish(videoId);
        return Result.ok();
    }

    /**
     * 更新video信息
     */
    @PostMapping("updateInfo")
    public Result<Void> updateInfo(@RequestBody Video newVideo) {
        videoService.updateVideo(newVideo);
        return Result.ok();
    }

    /**
     * 根据videoId获取视频详情
     */
    @GetMapping("getVideoDetail")
    public Result<VideoVO> getVideoDetail(@RequestParam String videoId) {
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
    @GetMapping("getOriginalFileDownloadUrl")
    public Result<JSONObject> getMyVideoList(@RequestParam String videoId) {
        String url = videoService.getOriginalFileDownloadUrl(videoId);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("url", url);
        return Result.ok(jsonObject);
    }
}
