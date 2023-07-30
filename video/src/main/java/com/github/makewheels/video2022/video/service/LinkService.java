package com.github.makewheels.video2022.video.service;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.file.FileRepository;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

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

    /**
     * 判断用户上传的原始文件md5是否存在
     */
    public boolean isOriginMd5VideoExist(String md5) {
        File oldFile = fileRepository.getByMd5(md5);
        // 如果数据库没查到这个md5，认为不存在
        if (oldFile == null) {
            return false;
        }

        // 如果OSS不存在，则认为不存在
        if (!fileService.doesOSSObjectExist(oldFile.getKey())) {
            return false;
        }

        // 校验原视频已就绪
        Video video = videoRepository.getById(oldFile.getVideoId());
        if (video == null) {
            return false;
        }
        return video.getStatus().equals(VideoStatus.READY);
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
