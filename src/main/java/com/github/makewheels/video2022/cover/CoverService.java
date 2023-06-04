package com.github.makewheels.video2022.cover;

import com.github.makewheels.video2022.file.FileService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CoverService {
    @Resource
    private CoverRepository coverRepository;
    @Resource
    private FileService fileService;

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
}
