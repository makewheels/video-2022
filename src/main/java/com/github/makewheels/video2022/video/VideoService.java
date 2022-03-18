package com.github.makewheels.video2022.video;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.usermicroservice2022.User;
import com.github.makewheels.video2022.file.File;
import com.github.makewheels.video2022.file.FileService;
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

    public String getWatchId() {
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
        return Result.ok(jsonObject);
    }

}
