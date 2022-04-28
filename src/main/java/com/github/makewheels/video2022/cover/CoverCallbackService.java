package com.github.makewheels.video2022.cover;


import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.file.AliyunOssService;
import com.github.makewheels.video2022.response.ErrorCode;
import com.github.makewheels.video2022.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

@Service
@Slf4j
public class CoverCallbackService {
    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private AliyunOssService aliyunOssService;

    public Result<Void> youtubeUploadFinishCallback(String coverId) {
        Cover cover = mongoTemplate.findById(coverId, Cover.class);
        if (cover == null) {
            return Result.error(ErrorCode.FAIL);
        }
        String key = cover.getKey();
        //向对象存储确认文件存在
        String coverProvider = cover.getProvider();
        if (StringUtils.equalsAny(coverProvider, CoverProvider.ALIYUN_CLOUD_FUNCTION, CoverProvider.ALIYUN_MPS)) {
            if (!aliyunOssService.doesObjectExist(key)) {
                return Result.error(ErrorCode.FAIL);
            }
        }
        //更新cover状态
        cover.setStatus(CoverStatus.READY);
        cover.setFinishTime(new Date());
        mongoTemplate.save(cover);
        log.info("成功处理搬运youtube封面回调，cover = {}", JSON.toJSONString(cover));
        return Result.ok();
    }
}
