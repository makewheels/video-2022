package com.github.makewheels.video2022.video;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baidubce.services.media.model.CreateThumbnailJobResponse;
import com.baidubce.services.media.model.CreateTranscodingJobResponse;
import com.baidubce.services.media.model.GetMediaInfoOfFileResponse;
import com.baidubce.services.media.model.VideoInfo;
import com.github.makewheels.usermicroservice2022.User;
import com.github.makewheels.usermicroservice2022.response.ErrorCode;
import com.github.makewheels.video2022.file.File;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.file.FileStatus;
import com.github.makewheels.video2022.response.Result;
import com.github.makewheels.video2022.thumbnail.Thumbnail;
import com.github.makewheels.video2022.thumbnail.ThumbnailService;
import com.github.makewheels.video2022.transcode.Resolution;
import com.github.makewheels.video2022.transcode.Transcode;
import com.github.makewheels.video2022.transcode.TranscodeService;
import com.github.makewheels.video2022.transcode.TranscodeStatus;
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
    @Resource
    private ThumbnailService thumbnailService;

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
        String key = "video/" + userId + "/" + videoId + "/original/" + videoId + "." + file.getExtension();
        file.setKey(key);
        file.setAccessUrl(fileService.getAccessBaseUrl() + key);
        file.setCdnUrl(fileService.getCdnBaseUrl() + key);
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
        String m3u8Key = "video/" + userId + "/" + videoId + "/transcode/"
                + resolution + "/" + videoId;
        transcode.setM3u8Key(m3u8Key);
        mongoTemplate.save(transcode);

        //发起转码
        CreateTranscodingJobResponse transcodingJob = transcodeService.createTranscodingJob(sourceKey,
                m3u8Key, resolution);
        String jobId = transcodingJob.getJobId();
        log.info("发起 " + resolution + " 转码：videoId = " + videoId);
        log.info(JSON.toJSONString(transcodingJob));
        transcode.setJobId(jobId);
        mongoTemplate.save(transcode);

        //再次更新数据库状态
        transcode.setStatus(transcodeService.getTranscodingJob(jobId).getJobStatus());
        mongoTemplate.save(transcode);
    }

    /**
     * 原始文件上传完成，开始转码
     *
     * @param user
     * @param videoId
     * @return
     */
    public Result<Void> originalFileUploadFinish(User user, String videoId) {
        String userId = user.getId();
        //查数据库，找到video
        Video video = mongoTemplate.findById(videoId, Video.class);
        if (video == null) return Result.error(ErrorCode.FAIL);

        File file = mongoTemplate.findById(video.getOriginalFileId(), File.class);
        if (file == null) return Result.error(ErrorCode.FAIL);

        if (!file.getStatus().equals(FileStatus.READY))
            return Result.error(ErrorCode.FAIL);

        String sourceKey = file.getKey();
        video.setOriginalFileKey(sourceKey);
        video.setStatus(VideoStatus.ORIGINAL_FILE_READY);
        mongoTemplate.save(video);


        //创建子线程执行转码任务，先给前端返回结果
        new Thread(() -> {
            //获取视频信息
            GetMediaInfoOfFileResponse mediaInfo = transcodeService.getMediaInfo(sourceKey);
            VideoInfo videoInfo = mediaInfo.getVideo();
            log.info("源文件上传完成，获取mediaInfo：videoId = " + videoId);
            log.info(JSON.toJSONString(mediaInfo));

            video.setMediaInfo(JSONObject.parseObject(JSON.toJSONString(mediaInfo)));
            mongoTemplate.save(video);

            //发起截帧任务
            String targetKeyPrefix = "";
            CreateThumbnailJobResponse thumbnailJob
                    = thumbnailService.createThumbnailJob(sourceKey, targetKeyPrefix);
            String thumbnailJobId = thumbnailJob.getJobId();
            Thumbnail thumbnail = new Thumbnail();
            String key = targetKeyPrefix + ".jpg";
            thumbnail.setCreateTime(new Date());
            thumbnail.setUserId(userId);
            thumbnail.setVideoId(videoId);
            thumbnail.setJobId(thumbnailJobId);
            thumbnail.setStatus(TranscodeStatus.CREATED);
            thumbnail.setSourceKey(sourceKey);
            thumbnail.setTargetKeyPrefix(targetKeyPrefix);
            thumbnail.setAccessUrl(fileService.getAccessBaseUrl() + key);
            thumbnail.setCdnUrl(fileService.getCdnBaseUrl() + key);
            thumbnail.setExtension("jpg");
            thumbnail.setKey(key);
            mongoTemplate.save(thumbnail);
            //再次查询，更新状态
            thumbnail.setStatus(thumbnailService.getThumbnailJob(thumbnailJobId).getJobStatus());
            mongoTemplate.save(thumbnail);

            Integer width = videoInfo.getWidthInPixel();
            Integer height = videoInfo.getHeightInPixel();

            //开始转码
            //首先一定会发起720p的转码
            transcode(user, video, Resolution.R_720P);

            //如果单边大于1280像素，再次发起1080p转码
            if (Math.max(width, height) > 1280) {
                transcode(user, video, Resolution.R_1080P);
            }
        }).start();

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

    public Result<Void> getPlayInfo(User user, String videoId) {
        String userId = user.getId();
        Video oldVideo = mongoTemplate.findById(videoId, Video.class);
        if (oldVideo == null || !StringUtils.equals(userId, oldVideo.getUserId())) {
            return Result.error(ErrorCode.FAIL);
        }
        mongoTemplate.save(oldVideo);
        return Result.ok();
    }

}
