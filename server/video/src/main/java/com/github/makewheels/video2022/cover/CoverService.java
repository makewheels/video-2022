package com.github.makewheels.video2022.cover;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.model.CannedAccessControlList;
import com.github.makewheels.video2022.file.FileRepository;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.file.constants.FileStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CoverService {
    @Resource
    private CoverRepository coverRepository;
    @Resource
    private FileService fileService;
    @Resource
    private FileRepository fileRepository;
    @Resource
    private MongoTemplate mongoTemplate;

    /**
     * 根据coverId，生成带对象存储签名的url
     */
    public String getSignedCoverUrl(String coverId) {
        if (coverId == null) return null;
        String key = coverRepository.getOssKey(coverId);
        if (key == null) return null;
        return fileService.generatePresignedUrl(key, Duration.ofHours(2));
    }

    /**
     * 批量生成预签名url
     * key: coverId
     * value: url
     */
    public Map<String, String> getSignedCoverUrl(List<String> coverIdList) {
        List<Cover> coverList = coverRepository.getByIdList(coverIdList);
        List<String> keyList = coverList.stream().map(Cover::getKey).collect(Collectors.toList());
        Map<String, String> key2UrlMap = fileService.generatePresignedUrl(keyList, Duration.ofHours(2));
        return coverList.stream().collect(Collectors.toMap(
                Cover::getId, cover -> key2UrlMap.get(cover.getKey())));
    }

    /**
     * 自建函数封面回调：幂等更新 Cover 与 File（字段级更新，不整文档覆盖视频状态）
     */
    public void applyFcCoverResult(Cover cover, JSONObject manifest) {
        String imageKey = manifest.getString("imageKey");

        cover.setStatus(CoverStatus.READY);
        cover.setFinishTime(new Date());
        cover.setResult(manifest);
        mongoTemplate.save(cover);

        File file = fileRepository.getById(cover.getFileId());
        if (file != null) {
            file.setAcl(CannedAccessControlList.PublicRead.toString());
            fileService.changeObjectAcl(file.getId(), CannedAccessControlList.PublicRead.toString());
            file.setObjectInfo(fileService.getObject(imageKey));
            file.setFileStatus(FileStatus.READY);
            mongoTemplate.save(file);
        }
        log.info("自建封面登记完成 coverId = {}, imageKey = {}", cover.getId(), imageKey);
    }
}
