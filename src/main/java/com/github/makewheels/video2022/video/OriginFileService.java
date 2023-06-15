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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 原始文件服务
 */
@Service
public class OriginFileService {
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
    public void onOriginFileUploadFinish(String videoId) {
        Video video = videoRepository.getById(videoId);
        File uploadNewFile = fileRepository.getById(video.getOriginalFileId());

        // 更新视频为正在转码状态
        video.setStatus(VideoStatus.TRANSCODING);
        mongoTemplate.save(video);

        // 获取文件md5
        String md5 = fileService.getMd5(uploadNewFile);
        uploadNewFile.setMd5(md5);
        mongoTemplate.save(uploadNewFile);

        // TODO 如果文件已存在，删除，放链接。
        // TODO 注意：task任务删除文件要考虑视频有效期
        // TODO 创建视频和播放视频链接有影响
        File md5OldFile = fileRepository.getByMd5(md5);
        if (md5OldFile != null) {
            fileService.deleteFile(uploadNewFile);
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
