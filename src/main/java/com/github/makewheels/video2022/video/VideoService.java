package com.github.makewheels.video2022.video;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.usermicroservice2022.User;
import com.github.makewheels.video2022.file.File;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.file.FileStatus;
import com.github.makewheels.video2022.response.Result;
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
        file.setKey("video/" + userId + "/" + videoId + "/original/" + videoId + "." + file.getExtension());
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
        //发起转码

        return Result.ok();
    }

}
