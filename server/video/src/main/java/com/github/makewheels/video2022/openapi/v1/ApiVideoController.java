package com.github.makewheels.video2022.openapi.v1;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.etc.check.CheckService;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.openapi.v1.dto.CreateVideoApiRequest;
import com.github.makewheels.video2022.openapi.v1.dto.UpdateVideoApiRequest;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.dto.CreateVideoDTO;
import com.github.makewheels.video2022.video.bean.dto.UpdateVideoInfoDTO;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.bean.vo.VideoVO;
import com.github.makewheels.video2022.video.service.VideoDeleteService;
import com.github.makewheels.video2022.video.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Videos", description = "视频管理")
@RestController
@RequestMapping("/api/v1/videos")
@Slf4j
public class ApiVideoController {
    @Resource
    private ApiAuthHelper apiAuthHelper;
    @Resource
    private VideoService videoService;
    @Resource
    private VideoDeleteService videoDeleteService;
    @Resource
    private FileService fileService;
    @Resource
    private CheckService checkService;
    @Resource
    private VideoRepository videoRepository;
    @Resource
    private MongoTemplate mongoTemplate;

    @Operation(summary = "创建视频")
    @PostMapping
    public Result<JSONObject> createVideo(@RequestBody CreateVideoApiRequest request) {
        try {
            apiAuthHelper.setupUserContext();
            CreateVideoDTO dto = new CreateVideoDTO();
            dto.setRawFilename(request.getRawFilename());
            dto.setSize(request.getSize());
            dto.setVideoType(request.getVideoType());
            dto.setTtl(request.getTtl());
            checkService.checkCreateVideoDTO(dto);
            JSONObject response = videoService.create(dto);
            return Result.ok(response);
        } finally {
            apiAuthHelper.clearUserContext();
        }
    }

    @Operation(summary = "获取视频列表")
    @GetMapping
    public Result<?> listVideos(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = apiAuthHelper.requireUserId();
        int skip = (page - 1) * size;
        int limit = Math.min(size, 100);
        try {
            apiAuthHelper.setupUserContext();
            return videoService.getMyVideoList(skip, limit, null);
        } finally {
            apiAuthHelper.clearUserContext();
        }
    }

    @Operation(summary = "获取视频详情")
    @GetMapping("/{videoId}")
    public Result<VideoVO> getVideo(@PathVariable String videoId) {
        checkService.checkVideoExist(videoId);
        VideoVO videoVO = videoService.getVideoDetail(videoId);
        return Result.ok(videoVO);
    }

    @Operation(summary = "更新视频信息")
    @PatchMapping("/{videoId}")
    public Result<Video> updateVideo(@PathVariable String videoId,
                                     @RequestBody UpdateVideoApiRequest request) {
        try {
            User user = apiAuthHelper.setupUserContext();
            checkService.checkVideoExist(videoId);
            checkService.checkVideoBelongsToUser(videoId, user.getId());
            UpdateVideoInfoDTO dto = new UpdateVideoInfoDTO();
            dto.setId(videoId);
            dto.setTitle(request.getTitle());
            dto.setDescription(request.getDescription());
            dto.setVisibility(request.getVisibility());
            dto.setTags(request.getTags());
            dto.setCategory(request.getCategory());
            Video video = videoService.updateVideo(dto);
            return Result.ok(video);
        } finally {
            apiAuthHelper.clearUserContext();
        }
    }

    @Operation(summary = "删除视频")
    @DeleteMapping("/{videoId}")
    public Result<Void> deleteVideo(@PathVariable String videoId) {
        try {
            User user = apiAuthHelper.setupUserContext();
            checkService.checkVideoExist(videoId);
            checkService.checkVideoBelongsToUser(videoId, user.getId());
            videoDeleteService.deleteVideo(videoId);
            return Result.ok();
        } finally {
            apiAuthHelper.clearUserContext();
        }
    }

    @Operation(summary = "获取上传凭证")
    @PostMapping("/{videoId}/upload-credentials")
    public Result<JSONObject> getUploadCredentials(@PathVariable String videoId) {
        try {
            User user = apiAuthHelper.setupUserContext();
            checkService.checkVideoExist(videoId);
            checkService.checkVideoBelongsToUser(videoId, user.getId());
            Video video = videoRepository.getById(videoId);
            JSONObject credentials = fileService.getUploadCredentials(video.getRawFileId());
            return Result.ok(credentials);
        } finally {
            apiAuthHelper.clearUserContext();
        }
    }

    @Operation(summary = "通知上传完成")
    @PostMapping("/{videoId}/upload-complete")
    public Result<Void> uploadComplete(@PathVariable String videoId) {
        try {
            User user = apiAuthHelper.setupUserContext();
            checkService.checkVideoExist(videoId);
            checkService.checkVideoBelongsToUser(videoId, user.getId());
            videoService.rawFileUploadFinish(videoId);
            return Result.ok();
        } finally {
            apiAuthHelper.clearUserContext();
        }
    }

    @Operation(summary = "获取转码状态")
    @GetMapping("/{videoId}/transcode")
    public Result<List<Transcode>> getTranscodeStatus(@PathVariable String videoId) {
        checkService.checkVideoExist(videoId);
        List<Transcode> transcodes = mongoTemplate.find(
                Query.query(Criteria.where("videoId").is(videoId)), Transcode.class);
        return Result.ok(transcodes);
    }

    @Operation(summary = "获取播放地址")
    @GetMapping("/{videoId}/play")
    public Result<JSONObject> getPlayUrl(@PathVariable String videoId) {
        checkService.checkVideoExist(videoId);
        Video video = videoRepository.getById(videoId);
        JSONObject data = new JSONObject();
        data.put("videoId", video.getId());
        data.put("status", video.getStatus());
        if (video.getWatch() != null) {
            data.put("watchUrl", video.getWatch().getWatchUrl());
            data.put("shortUrl", video.getWatch().getShortUrl());
            data.put("watchId", video.getWatch().getWatchId());
        }
        return Result.ok(data);
    }
}
