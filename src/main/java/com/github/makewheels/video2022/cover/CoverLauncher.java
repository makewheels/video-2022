package com.github.makewheels.video2022.cover;

import com.alibaba.fastjson.JSON;
import com.baidubce.services.media.model.CreateThumbnailJobResponse;
import com.github.makewheels.usermicroservice2022.user.User;
import com.github.makewheels.video2022.file.File;
import com.github.makewheels.video2022.file.FileStatus;
import com.github.makewheels.video2022.file.S3Provider;
import com.github.makewheels.video2022.utils.PathUtil;
import com.github.makewheels.video2022.video.YoutubeService;
import com.github.makewheels.video2022.video.bean.Video;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import com.github.makewheels.video2022.video.constants.VideoType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

@Service
@Slf4j
public class CoverLauncher {
    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private BaiduCoverService baiduCoverService;
    @Resource
    private YoutubeService youtubeService;

    @Value("${baidu.bos.accessBaseUrl}")
    private String baiduBosAccessBaseUrl;
    @Value("${baidu.bos.cdnBaseUrl}")
    private String baiduBosCdnBaseUrl;
    @Value("${aliyun.oss.accessBaseUrl}")
    private String aliyunOssAccessBaseUrl;
    @Value("${aliyun.oss.cdnBaseUrl}")
    private String aliyunOssCdnBaseUrl;

    /**
     * 发起截帧任务
     * 如果是youtube搬运视频，向海外服务器发起请求
     * 如果用户自己上传的视频：
     * 如果文件在百度对象存储，用百度截图
     * 如果文件在阿里云对象存储，用云函数
     */
    public void createCover(User user, Video video) {
        String userId = user.getId();
        String videoId = video.getId();
        String sourceKey = video.getOriginalFileKey();
        String videoProvider = video.getProvider();
        String videoType = video.getType();

        //先创建cover和file对象
        Cover cover = new Cover();
        cover.setCreateTime(new Date());
        cover.setUserId(userId);
        cover.setVideoId(videoId);
        cover.setStatus(VideoStatus.CREATED);
        cover.setSourceKey(sourceKey);

        File file = new File();
        file.setCreateTime(new Date());
        file.setStatus(FileStatus.CREATED);
        file.setUserId(userId);
        file.setVideoId(videoId);

        //判断类型
        //如果是youtube搬运
        if (videoType.equals(VideoType.YOUTUBE) && videoProvider.equals(S3Provider.ALIYUN_OSS)) {
            handleYoutubeCover(user, video, cover, file);
            //如果是用户自己上传
        } else if (videoType.equals(VideoType.USER_UPLOAD)) {
            if (videoProvider.equals(S3Provider.ALIYUN_OSS)) {
                handleCloudFunctionCover(user, video, cover, file);
            } else if (videoProvider.equals(S3Provider.BAIDU_BOS)) {
                handleBaiduCover(user, video, cover);
            }
        }

        //更新video的封面地址coverUrl
        video.setCoverUrl(cover.getCdnUrl());
        mongoTemplate.save(video);
    }

    /**
     * 生成封面：youtube搬运
     */
    private void handleYoutubeCover(User user, Video video, Cover cover, File file) {

    }

    /**
     * 生成封面：阿里云云函数
     */
    private void handleCloudFunctionCover(User user, Video video, Cover cover, File file) {

    }

    /**
     * 生成封面：百度截帧
     */
    private void handleBaiduCover(User user, Video video, Cover cover) {
        String userId = user.getId();
        String videoId = video.getId();
        String sourceKey = video.getOriginalFileKey();
        cover.setExtension("jpg");
        cover.setProvider(S3Provider.BAIDU_BOS);

        String targetKeyPrefix = PathUtil.getS3VideoPrefix(userId, videoId) + "/cover/" + videoId;
        CreateThumbnailJobResponse thumbnailJob = baiduCoverService.createThumbnailJob(sourceKey, targetKeyPrefix);
        log.info("通过百度云发起截帧任务：CreateThumbnailJobResponse = " + JSON.toJSONString(thumbnailJob));

        String key = targetKeyPrefix + ".jpg";
        cover.setKey(key);
        cover.setAccessUrl(baiduBosAccessBaseUrl + key);
        cover.setCdnUrl(baiduBosCdnBaseUrl + key);

        String thumbnailJobId = thumbnailJob.getJobId();
        cover.setJobId(thumbnailJobId);

        mongoTemplate.save(cover);
        //再次查询，更新状态
        cover.setStatus(baiduCoverService.getThumbnailJob(thumbnailJobId).getJobStatus());
        mongoTemplate.save(cover);
    }
}
