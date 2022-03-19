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
import com.github.makewheels.video2022.transcode.TranscodeService;
import com.tencentcloudapi.mps.v20190612.models.DescribeMediaMetaDataResponse;
import com.tencentcloudapi.mps.v20190612.models.MediaMetaData;
import lombok.extern.slf4j.Slf4j;
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

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("fileId", fileId);
        jsonObject.put("videoId", video.getId());
        return Result.ok(jsonObject);
    }

    public Result<Void> originalFileUploadFinish(User user, String videoId) {
        Video video = mongoTemplate.findById(videoId, Video.class);
        File file = mongoTemplate.findById(video.getOriginalFileId(), File.class);
        if (file.getStatus().equals(FileStatus.READY)) {
            video.setStatus(VideoStatus.ORIGINAL_FILE_READY);
            mongoTemplate.save(video);
        }

        //获取视频信息
        MediaMetaData metaData = transcodeService.describeMediaMetaData(file.getKey()).getMetaData();
        String metaDataJson = JSON.toJSONString(metaData);
        log.info("videoId = " + videoId + " 获取到metaData：");
        log.info(metaDataJson);

        video.setMetaData(JSONObject.parseObject(metaDataJson));
        mongoTemplate.save(video);
        Long width = metaData.getWidth();
        Long height = metaData.getHeight();
        long multiplication = width * height;
        //发起转码
        //如果大于720p，发起两次转码
        if (multiplication > 1280 * 720) {

        }

        return Result.ok();
    }

    public Result<Video> getById(User user, String videoId) {
        Video video = mongoTemplate.findById(videoId, Video.class);
        if (video == null)
            return Result.error(ErrorCode.FAIL);
        video.setMetaData(null);
        return Result.ok(video);
    }

}
