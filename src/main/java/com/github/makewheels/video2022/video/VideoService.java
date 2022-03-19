package com.github.makewheels.video2022.video;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.usermicroservice2022.User;
import com.github.makewheels.usermicroservice2022.response.ErrorCode;
import com.github.makewheels.video2022.file.File;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.file.FileStatus;
import com.github.makewheels.video2022.response.Result;
import com.github.makewheels.video2022.transcode.Resolution;
import com.github.makewheels.video2022.transcode.Transcode;
import com.github.makewheels.video2022.transcode.TranscodeService;
import com.github.makewheels.video2022.transcode.TranscodeStatus;
import com.tencentcloudapi.mps.v20190612.models.MediaMetaData;
import com.tencentcloudapi.mps.v20190612.models.ProcessMediaResponse;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

@Service
@Slf4j
public class VideoService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private FileService fileService;
    @Resource
    private TranscodeService transcodeService;

    private String getWatchId() {
        return IdUtil.simpleUUID();
    }

    public Result<JSONObject> create(User user, JSONObject requestBody) {
        String userId = user.getId();
        //创建 file
        File file = fileService.create(user, requestBody.getString("originalFilename"));

        String fileId = file.getId();
        //创建 video
        Video video = new Video();
        video.setOriginalFileId(fileId);
        video.setUserId(userId);
        video.setWatchId(getWatchId());
        video.setStatus(VideoStatus.CREATED);
        video.setCreateTime(new Date());
        mongoTemplate.save(video);

        String videoId = video.getId();
        // 更新file上传路径
        String key = "/video/" + userId + "/" + videoId + "/original/" + videoId + "." + file.getExtension();
        file.setKey(key);
        file.setAccessUrl("https://video-2022-1253319037.cos.ap-beijing.myqcloud.com" + key);
        file.setCdnUrl("https://video-2022-1253319037.file.myqcloud.com" + key);
        mongoTemplate.save(file);

        //更新video originalFileKey
        video.setOriginalFileKey(key);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("fileId", fileId);
        jsonObject.put("videoId", video.getId());
        return Result.ok(jsonObject);
    }

    /**
     * 转码任务，盲分辨率，子线程异步执行
     *
     * @param user
     * @param video
     * @param resolution
     */
    private void transcode(User user, Video video, String resolution) {
        new Thread(() -> {
            String userId = user.getId();
            String videoId = video.getId();
            String sourceKey = video.getOriginalFileKey();

            Transcode transcode = new Transcode();
            transcode.setUserId(userId);
            transcode.setVideoId(videoId);
            transcode.setCreateTime(new Date());
            transcode.setStatus(TranscodeStatus.CREATED);
            transcode.setResolution(resolution);
            transcode.setSourceKey(sourceKey);
            String outputDir = "/video/" + userId + "/" + videoId + "/transcode/" + resolution + "/";
            transcode.setOutputDir(outputDir);
            mongoTemplate.save(transcode);

            //发起转码
            ProcessMediaResponse processMedia = transcodeService.processMedia(sourceKey,
                    outputDir, resolution);
            log.info("发起 " + resolution + " 转码：videoId = " + videoId);
            log.info(JSON.toJSONString(processMedia));
            transcode.setTaskId(processMedia.getTaskId());
            mongoTemplate.save(transcode);

            //再次查询状态
            transcode.setStatus(transcodeService.describeTaskDetail(processMedia.getTaskId()).getStatus());
            mongoTemplate.save(transcode);
        }).start();
    }

    public Result<Void> originalFileUploadFinish(User user, String videoId) {
        //查数据库，找到video
        Video video = mongoTemplate.findById(videoId, Video.class);
        File file = mongoTemplate.findById(video.getOriginalFileId(), File.class);
        if (file.getStatus().equals(FileStatus.READY)) {
            video.setOriginalFileKey(file.getKey());
            video.setStatus(VideoStatus.ORIGINAL_FILE_READY);
            mongoTemplate.save(video);
        } else {
            return Result.error(ErrorCode.FAIL);
        }

        //获取视频信息
        MediaMetaData metaData = transcodeService.describeMediaMetaData(file.getKey()).getMetaData();
        String metaDataJson = JSON.toJSONString(metaData);
        log.info("源文件上传完成，获取metaData：videoId = " + videoId);
        log.info(metaDataJson);

        video.setMetaData(JSONObject.parseObject(metaDataJson));
        mongoTemplate.save(video);
        Long width = metaData.getWidth();
        Long height = metaData.getHeight();

        //开始转码
        //首先，一定会发起720p的转码
        transcode(user, video, Resolution.R_720P);

        //如果单边大于1280像素，再发起1080p转码
        if (Math.max(width, height) > 1280) {
            transcode(user, video, Resolution.R_1080P);
        }

        return Result.ok();
    }

    public Result<Void> updateVideo(User user, Video updateVideo) {
        String userId = user.getId();
        String videoId = updateVideo.getId();
        Video oldVideo = mongoTemplate.findById(videoId, Video.class);
        if (oldVideo == null || !StringUtils.equals(userId, oldVideo.getUserId())) {
            return Result.error(ErrorCode.FAIL);
        }
        oldVideo.setTitle(updateVideo.getTitle());
        oldVideo.setDescription(updateVideo.getDescription());
        mongoTemplate.save(oldVideo);
        return Result.ok();
    }

    public Result<Video> getById(User user, String videoId) {
        Video video = mongoTemplate.findById(videoId, Video.class);
        if (video == null)
            return Result.error(ErrorCode.FAIL);
        video.setMetaData(null);
        video.setOriginalFileId(null);
        video.setOriginalFileKey(null);
        return Result.ok(video);
    }

}
