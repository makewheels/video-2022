package com.github.makewheels.video2022.video.service;

import com.github.makewheels.video2022.cover.CoverLauncher;
import com.github.makewheels.video2022.file.FileRepository;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.transcode.TranscodeLauncher;
import com.github.makewheels.video2022.user.UserRepository;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import com.github.makewheels.video2022.video.constants.VideoType;
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
        log.info("用户原始文件上传完成，进入开始处理总入口, videoId = {}, uploadNewFile = {} ", videoId, newFile.getId());

        // 更新视频为 [准备转码] 状态
        updateVideoStatus(newVideo, VideoStatus.PREPARE_TRANSCODING);

        // 同步调用阿里云云函数，获取文件md5
        String md5 = fileService.getMd5(newFile);
        newFile.setMd5(md5);

        // TODO 创建视频和播放视频链接有影响

        if (isFileMd5Exist(md5)) {
            fileRepository.updateMd5(newFile.getId(), md5);
            File oldFile = fileRepository.getByMd5(md5);

            log.info("用户上传原始文件md5已存在, 用户上传新文件id = {}, 老的已存在文件id = {}, md5 = {}",
                    newFile.getId(), oldFile.getId(), md5);
            // 设置file链接
            newFile.setHasLink(true);
            newFile.setLinkFileId(oldFile.getId());
            newFile.setLinkFileKey(oldFile.getKey());
            mongoTemplate.save(newFile);

            // 不再发起转码，设置视频链接
            newVideo.getLink().setHasLink(true);
            newVideo.getLink().setLinkVideoId(oldFile.getVideoId());
            log.info("设置视频链接, oldVideoId = {}, newVideoId = {}", oldFile.getVideoId(), newVideo.getId());

            // 设置transcodeId
            Video oldVideo = videoRepository.getById(oldFile.getVideoId());
            newVideo.setTranscodeIds(oldVideo.getTranscodeIds());
            newVideo.setCoverId(oldVideo.getCoverId());
            newVideo.setOwnerId(oldVideo.getOwnerId());
            mongoTemplate.save(newVideo);

            // 删除新上传的OSS文件
            fileService.deleteFile(newFile);

            // 更新视频为 [就绪] 状态
            updateVideoStatus(newVideo, VideoStatus.READY);

            // 视频就绪回调
            videoReadyService.onVideoReady(newVideo.getId());
        } else {
            fileRepository.updateMd5(newFile.getId(), md5);

            // 发起转码
            User user = userRepository.getById(newVideo.getUploaderId());
            transcodeLauncher.transcodeVideo(user, newVideo);

            // 更新视频为 [正在转码] 状态
            updateVideoStatus(newVideo, VideoStatus.TRANSCODING);

            //封面：如果是youtube视频，之前创建的时候已经搬运封面了，用户上传视频要截帧
            if (!VideoType.YOUTUBE.equals(newVideo.getStatus())) {
                coverLauncher.createCover(user, newVideo);
            }
        }
    }
}
