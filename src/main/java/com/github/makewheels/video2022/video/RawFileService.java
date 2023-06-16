package com.github.makewheels.video2022.video;

import com.github.makewheels.video2022.cover.CoverLauncher;
import com.github.makewheels.video2022.file.FileRepository;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.transcode.TranscodeLauncher;
import com.github.makewheels.video2022.user.UserRepository;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.VideoStatus;
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


    /**
     * 用户上传视频文件后，开始处理的总入口
     */
    public void onRawFileUploadFinish(String videoId) {
        Video video = videoRepository.getById(videoId);
        File uploadNewFile = fileRepository.getById(video.getRawFileId());
        log.info("用户原始文件上传完成，进入开始处理总入口，videoId = {}，uploadNewFile = {}, videoTitle = {}",
                videoId, uploadNewFile.getId(), video.getTitle());

        // 更新视频为正在转码状态
        video.setStatus(VideoStatus.TRANSCODING);
        videoRepository.updateStatus(videoId, VideoStatus.TRANSCODING);

        // 同步调用阿里云云函数，获取文件md5
        String md5 = fileService.getMd5(uploadNewFile);
        uploadNewFile.setMd5(md5);
        fileRepository.updateMd5(uploadNewFile.getId(), md5);

        // TODO 如果文件已存在，删除，放链接。
        // TODO 注意：task任务删除文件要考虑视频有效期
        // TODO 创建视频和播放视频链接有影响
        File md5OldFile = fileRepository.getByMd5(md5);
        if (md5OldFile != null) {
            log.info("用户上传原始文件md5已存在, 用户上传文件id = {}, 老的已存在文件id = {}, md5 = {}",
                    uploadNewFile.getId(), md5OldFile.getId(), md5);
//            fileService.deleteFile(uploadNewFile);
            // TODO 放链接

        }

        User user = userRepository.getById(video.getUploaderId());
        transcodeLauncher.transcodeVideo(user, video);
        //封面：如果是youtube视频，之前创建的时候已经搬运封面了，用户上传视频要截帧
        if (!video.isYoutube()) {
            coverLauncher.createCover(user, video);
        }
    }
}
