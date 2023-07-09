package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.video.service;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.cover.CoverLauncher;
import com.github.makewheels.video2022.file.FileRepository;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.transcode.TranscodeLauncher;
import com.github.makewheels.video2022.user.UserRepository;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.videopackage.VideoRepository;
import com.github.makewheels.video2022.videopackage.bean.entity.Video;
import com.github.makewheels.video2022.videopackage.constants.VideoStatus;
import com.github.makewheels.video2022.videopackage.constants.VideoType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 原始文件服务
 */
@Service
@Slf4j
public class RawFileService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private VideoRepository videoRepository;
    @Resource
    private UserRepository userRepository;
    @Resource
    private FileRepository fileRepository;

    @Resource
    private TranscodeLauncher transcodeLauncher;
    @Resource
    private CoverLauncher coverLauncher;

    @Resource
    private FileService fileService;
    @Resource
    private VideoReadyService videoReadyService;

    /**
     * 判断用户上传的原始文件是否存在
     */
    private boolean isFileMd5Exist(String md5) {
        File oldFile = fileRepository.getByMd5(md5);
        // 如果数据库没查到这个md5，认为不存在
        if (oldFile == null) {
            return false;
        }
        // 如果在数据库查到这个md5了，再向OSS确认是否存在
        return fileService.doesOSSObjectExist(oldFile.getKey());
    }

    /**
     * 更新视频状态
     */
    private void updateVideoStatus(Video video, String status) {
        video.setStatus(status);
        videoRepository.updateStatus(video.getId(), status);
    }

    /**
     * 用户上传视频文件后，开始处理的总入口
     */
    public void onRawFileUploadFinish(String videoId) {
        Video newVideo = videoRepository.getById(videoId);
        File newFile = fileRepository.getById(newVideo.getRawFileId());
        log.info("用户原始文件上传完成，进入开始处理总入口, videoId = {}, newFileId = {} ", videoId, newFile.getId());

        // 更新视频为 [准备转码] 状态
        updateVideoStatus(newVideo, VideoStatus.PREPARE_TRANSCODING);

        // 同步调用阿里云云函数，获取文件md5
        String md5 = fileService.getMd5(newFile);
        newFile.setMd5(md5);

        // md5是否存在
        boolean fileMd5Exist = isFileMd5Exist(md5);
        File oldFile = fileRepository.getByMd5(md5);
        fileRepository.updateMd5(newFile.getId(), md5);
        if (fileMd5Exist) {
            // 如果文件md5已存在，创建链接
            createLink(newVideo, newFile, oldFile);
        } else {
            // 如果是新文件，发起转码
            launchTranscode(newVideo);
        }
    }

    /**
     * 原文件已存在，创建链接
     */
    private void createLink(Video newVideo, File newFile, File oldFile) {
        log.info("用户上传原始文件md5已存在, 用户上传新文件id = {}, 老的已存在文件id = {}, md5 = {}",
                newFile.getId(), oldFile.getId(), newFile.getMd5());
        // 链接 file
        newFile.setHasLink(true);
        newFile.setLinkFileId(oldFile.getId());
        newFile.setLinkFileKey(oldFile.getKey());
        log.info("链接newFile = " + JSON.toJSONString(newFile));
        mongoTemplate.save(newFile);
        // 删除新上传的OSS文件
        fileService.deleteFile(newFile);

        // 链接 video
        newVideo.getLink().setHasLink(true);
        String oldVideoId = oldFile.getVideoId();
        newVideo.getLink().setLinkVideoId(oldVideoId);
        Video oldVideo = videoRepository.getById(oldVideoId);
        newVideo.setTranscodeIds(oldVideo.getTranscodeIds());
        newVideo.setCoverId(oldVideo.getCoverId());
        newVideo.setOwnerId(oldVideo.getOwnerId());
        log.info("链接 newVideo = " + JSON.toJSONString(newVideo));
        mongoTemplate.save(newVideo);

        // 更新视频为 [就绪] 状态
        updateVideoStatus(newVideo, VideoStatus.READY);
        // 视频就绪回调
        videoReadyService.onVideoReady(newVideo.getId());
    }

    /**
     * 原文件不存在，发起转码
     */
    private void launchTranscode(Video newVideo) {
        // 更新视频为 [正在转码] 状态
        updateVideoStatus(newVideo, VideoStatus.TRANSCODING);

        // 发起转码
        User user = userRepository.getById(newVideo.getUploaderId());
        transcodeLauncher.transcodeVideo(user, newVideo);

        //封面：如果是youtube视频，之前创建的时候已经搬运封面了，用户上传视频要截帧
        if (!VideoType.YOUTUBE.equals(newVideo.getStatus())) {
            coverLauncher.createCover(user, newVideo);
        }
    }
}
