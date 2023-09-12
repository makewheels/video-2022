package com.github.makewheels.video2022.cover;

import cn.hutool.core.io.file.FileNameUtil;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.mts20140618.models.QuerySnapshotJobListResponseBody;
import com.aliyun.mts20140618.models.SubmitSnapshotJobResponse;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.file.constants.FileType;
import com.github.makewheels.video2022.file.constants.ObjectStorageProvider;
import com.github.makewheels.video2022.etc.system.environment.EnvironmentService;
import com.github.makewheels.video2022.transcode.aliyun.AliyunMpsService;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.utils.IdService;
import com.github.makewheels.video2022.utils.OssPathUtil;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.VideoType;
import com.github.makewheels.video2022.video.service.YoutubeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

/**
 * 发起截帧
 */
@Service
@Slf4j
public class CoverLauncher {
    @Resource
    private EnvironmentService environmentService;
    @Value("${aliyun.oss.video.accessBaseUrl}")
    private String aliyunOssAccessBaseUrl;

    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private FileService fileService;

    @Resource
    private YoutubeService youtubeService;
    @Resource
    private AliyunMpsService aliyunMpsService;
    @Resource
    private CoverCallbackService coverCallbackService;
    @Resource
    private IdService idService;

    /**
     * 发起截帧任务
     * 如果是youtube搬运视频，向海外服务器发起请求
     * 如果用户自己上传的视频：
     * 如果文件在阿里云对象存储，用云函数
     */
    public void createCover(User user, Video video) {
        String userId = user.getId();
        String videoId = video.getId();
        String videoProvider = video.getProvider();
        String videoType = video.getVideoType();

        //创建file和cover对象
        File file = new File();
        file.setId(idService.getFileId());
        file.setUploaderId(userId);
        file.setVideoId(videoId);
        file.setFileType(FileType.COVER);
        file.setVideoType(videoType);
        mongoTemplate.save(file);

        Cover cover = new Cover();
        cover.setId(idService.getCoverId());
        cover.setCreateTime(new Date());
        cover.setUserId(userId);
        cover.setVideoId(videoId);
        cover.setStatus(CoverStatus.CREATED);
        cover.setFileId(file.getId());
        mongoTemplate.save(cover);

        //判断类型
        //如果是youtube搬运
        if (videoType.equals(VideoType.YOUTUBE)
                && videoProvider.equals(ObjectStorageProvider.ALIYUN_OSS)) {
            handleYoutubeCover(user, video, cover, file);
            //如果是用户自己上传
        } else if (videoType.equals(VideoType.USER_UPLOAD)) {
            if (videoProvider.equals(ObjectStorageProvider.ALIYUN_OSS)) {
                handleAliyunMpsCover(user, video, cover, file);
            }
        }

        //更新video
        video.setCoverId(cover.getId());
        mongoTemplate.save(video);
    }

    /**
     * 生成封面：youtube搬运
     */
    private void handleYoutubeCover(User user, Video video, Cover cover, File file) {
        String coverId = cover.getId();
        String videoProvider = video.getProvider();
        cover.setProvider(CoverProvider.YOUTUBE);
        //获取youtube下载url
        JSONObject snippet = video.getYouTube().getVideoInfo().getJSONObject("snippet");
        String downloadUrl = snippet.getJSONObject("thumbnails")
                .getJSONObject("standard").getString("url");
        //设置cover
        String extension = FileNameUtil.extName(downloadUrl);
        cover.setExtension(extension);
        String coverKey = OssPathUtil.getCoverKey(video, cover, file);
        cover.setKey(coverKey);
        if (videoProvider.equals(ObjectStorageProvider.ALIYUN_OSS)) {
            cover.setAccessUrl(aliyunOssAccessBaseUrl + coverKey);
        }
        //更新cover
        mongoTemplate.save(cover);

        //更新file
        file.setKey(coverKey);
        file.setExtension(extension);
        file.setRawFilename(FileNameUtil.getName(downloadUrl));
        file.setProvider(videoProvider);
        file.setVideoType(video.getVideoType());
        mongoTemplate.save(file);

        //发起请求，搬运youtube封面
        String path = "/cover/youtubeUploadFinishCallback?" +
                "coverId=" + coverId + "&token=" + user.getToken();
        String businessUploadFinishCallbackUrl = environmentService.getCallbackUrl(path);
        log.info("发起youtube搬运封面请求：downloadUrl = {}", downloadUrl);
        youtubeService.transferFile(user, file, downloadUrl, businessUploadFinishCallbackUrl);
    }

    /**
     * 生成封面：阿里云 MPS
     */
    private void handleAliyunMpsCover(User user, Video video, Cover cover, File file) {
        cover.setExtension("jpg");
        String targetKey = OssPathUtil.getCoverKey(video, cover, file);

        String rawFileKey = fileService.getKeyByFileId(video.getRawFileId());
        SubmitSnapshotJobResponse response = aliyunMpsService.submitSnapshotJob(rawFileKey, targetKey);
        String jobId = response.getBody().getSnapshotJob().getId();

        //调一次阿里云接口，查询一次status，更新到数据库的cover
        QuerySnapshotJobListResponseBody.QuerySnapshotJobListResponseBodySnapshotJobListSnapshotJob
                job = aliyunMpsService.simpleQueryOneJob(jobId);
        String jobStatus = job.getState();
        log.info("截图任务已提交: videoId = {}, jobId = {}, title = {}",
                video.getId(), jobId, video.getTitle());

        //更新cover
        cover.setJobId(jobId);
        cover.setStatus(jobStatus);
        cover.setKey(targetKey);
        cover.setAccessUrl(aliyunOssAccessBaseUrl + targetKey);
        mongoTemplate.save(cover);

        //更新file
        file.setKey(targetKey);
        mongoTemplate.save(file);

        //迭代查询结果
        coverCallbackService.iterateQueryAliyunSnapshotJob(video, cover);
    }

}
