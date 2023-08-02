package com.github.makewheels.video2022.video.service;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.file.FileRepository;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.transcode.TranscodeRepository;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.transcode.contants.TranscodeStatus;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 源文件md5相同，链接服务
 */
@Service
@Slf4j
public class LinkService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private FileRepository fileRepository;
    @Resource
    private VideoRepository videoRepository;
    @Resource
    private FileService fileService;
    @Resource
    private TranscodeRepository transcodeRepository;

    /**
     * 判断用户上传的原始文件md5是否存在
     */
    public boolean isOriginVideoExist(String md5) {
        // 原始文件md5是否存在
        File oldFile = fileRepository.getByMd5(md5);
        if (oldFile == null) {
            return false;
        }

        // 原始文件在OSS是否存在
        if (!fileService.doesOSSObjectExist(oldFile.getKey())) {
            return false;
        }

        // 原视频是否就绪
        Video video = videoRepository.getById(oldFile.getVideoId());
        if (video == null || !video.getStatus().equals(VideoStatus.READY)) {
            return false;
        }

        // 转码是否存在
        List<Transcode> transcodeList = transcodeRepository.getByIds(video.getTranscodeIds());
        for (Transcode transcode : transcodeList) {
            // transcode状态是否就绪
            if (!TranscodeStatus.isFinishStatus(transcode.getStatus())) {
                return false;
            }
            // transcode的m3u8文件在OSS是否存在
            if (!fileService.doesOSSObjectExist(transcode.getM3u8Key())) {
                return false;
            }
        }

        // 通过以上所有校验，认为存在
        return true;
    }

    /**
     * 链接 file
     */
    public void linkFile(File newFile, File oldFile) {
        newFile.setHasLink(true);
        newFile.setLinkFileId(oldFile.getId());
        newFile.setLinkFileKey(oldFile.getKey());
        log.info("链接newFile = " + JSON.toJSONString(newFile));
        mongoTemplate.save(newFile);
    }

    /**
     * 链接 video
     */
    private void linkVideo(Video newVideo, File oldFile) {
        newVideo.getLink().setHasLink(true);
        newVideo.getLink().setLinkVideoId(oldFile.getVideoId());

        Video oldVideo = videoRepository.getById(oldFile.getVideoId());
        newVideo.setTranscodeIds(oldVideo.getTranscodeIds());
        newVideo.setCoverId(oldVideo.getCoverId());
        newVideo.setOwnerId(oldVideo.getOwnerId());
        log.info("链接 newVideo = " + JSON.toJSONString(newVideo));
        mongoTemplate.save(newVideo);
    }

    /**
     * 创建文件和视频链接
     */
    public void createFileAndVideoLink(Video newVideo, File newFile, File oldFile) {
        log.info("用户上传原始文件md5已存在, 用户上传新文件id = {}, 老的已存在文件id = {}, md5 = {}",
                newFile.getId(), oldFile.getId(), newFile.getMd5());
        linkFile(newFile, oldFile);

        linkVideo(newVideo, oldFile);
    }

}
