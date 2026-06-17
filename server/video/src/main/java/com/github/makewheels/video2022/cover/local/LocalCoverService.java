package com.github.makewheels.video2022.cover.local;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.cover.Cover;
import com.github.makewheels.video2022.cover.CoverProvider;
import com.github.makewheels.video2022.cover.CoverRepository;
import com.github.makewheels.video2022.cover.CoverStatus;
import com.github.makewheels.video2022.cover.local.dto.CreateLocalCoverRequest;
import com.github.makewheels.video2022.etc.check.CheckService;
import com.github.makewheels.video2022.file.FileRepository;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.file.constants.FileStatus;
import com.github.makewheels.video2022.file.constants.FileType;
import com.github.makewheels.video2022.springboot.exception.VideoException;
import com.github.makewheels.video2022.system.response.ErrorCode;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.utils.IdService;
import com.github.makewheels.video2022.utils.OssPathUtil;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.entity.Video;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.Date;

/**
 * 本地封面服务：客户端用本机 FFmpeg 截好封面后回传，服务端只做登记，不调用 MPS 截帧。
 *
 * <pre>
 *   1. createCover：登记 Cover/File，返回 coverKey 与 OSS 上传凭证
 *   2. 客户端把截好的封面图直传到 coverKey
 *   3. finishCover：置封面就绪，并把 coverId 挂到视频上
 * </pre>
 */
@Service
@Slf4j
public class LocalCoverService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private VideoRepository videoRepository;
    @Resource
    private CoverRepository coverRepository;
    @Resource
    private FileService fileService;
    @Resource
    private FileRepository fileRepository;
    @Resource
    private IdService idService;
    @Resource
    private CheckService checkService;

    @Value("${aliyun.oss.video.accessBaseUrl}")
    private String aliyunOssAccessBaseUrl;

    /**
     * 登记封面记录，返回上传目标与凭证
     */
    public JSONObject createCover(CreateLocalCoverRequest request) {
        String videoId = request.getVideoId();
        checkService.checkVideoExist(videoId);
        checkService.checkVideoBelongsToUser(videoId, UserHolder.getUserId());

        Video video = videoRepository.getById(videoId);
        String extension = StringUtils.hasText(request.getExtension()) ? request.getExtension() : "jpg";

        File file = new File();
        file.setId(idService.getFileId());
        file.setUploaderId(video.getUploaderId());
        file.setVideoId(videoId);
        file.setFileType(FileType.COVER);
        file.setVideoType(video.getVideoType());
        file.setExtension(extension);
        mongoTemplate.save(file);

        Cover cover = new Cover();
        cover.setId(idService.getCoverId());
        cover.setUserId(video.getUploaderId());
        cover.setVideoId(videoId);
        cover.setFileId(file.getId());
        cover.setProvider(CoverProvider.LOCAL);
        cover.setStatus(CoverStatus.CREATED);
        cover.setExtension(extension);

        String coverKey = OssPathUtil.getCoverKey(video, cover, file);
        cover.setKey(coverKey);
        cover.setAccessUrl(aliyunOssAccessBaseUrl + coverKey);
        mongoTemplate.save(cover);

        file.setKey(coverKey);
        mongoTemplate.save(file);

        JSONObject credentials = fileService.getUploadCredentialsByKey(coverKey);

        JSONObject response = new JSONObject();
        response.put("coverId", cover.getId());
        response.put("coverKey", coverKey);
        response.put("uploadCredentials", credentials);
        log.info("登记本地封面，videoId = {}, coverId = {}, coverKey = {}", videoId, cover.getId(), coverKey);
        return response;
    }

    /**
     * 封面上传完成回调：置就绪并挂到视频
     */
    public void finishCover(String coverId) {
        Cover cover = coverRepository.getById(coverId);
        if (cover == null) {
            throw new VideoException(ErrorCode.COVER_NOT_EXIST, "封面不存在: " + coverId);
        }
        checkService.checkVideoBelongsToUser(cover.getVideoId(), UserHolder.getUserId());

        cover.setStatus(CoverStatus.READY);
        cover.setFinishTime(new Date());
        mongoTemplate.save(cover);

        File file = fileRepository.getById(cover.getFileId());
        if (file != null) {
            file.setFileStatus(FileStatus.READY);
            mongoTemplate.save(file);
        }

        Video video = videoRepository.getById(cover.getVideoId());
        video.setCoverId(coverId);
        mongoTemplate.save(video);
        log.info("本地封面就绪，videoId = {}, coverId = {}", cover.getVideoId(), coverId);
    }
}
