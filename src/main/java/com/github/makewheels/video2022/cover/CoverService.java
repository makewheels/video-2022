package com.github.makewheels.video2022.cover;

import com.github.makewheels.video2022.file.FileService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;

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
        Cover cover = coverRepository.getById(coverId);
        if (cover == null) return null;
        String key = cover.getKey();
        return fileService.generatePresignedUrl(key, Duration.ofHours(2));
    }
}
