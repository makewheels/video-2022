package com.github.makewheels.video2022.video;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.cover.CoverLauncher;
import com.github.makewheels.video2022.etc.response.ErrorCode;
import com.github.makewheels.video2022.exception.VideoException;
import com.github.makewheels.video2022.file.File;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.file.constants.FileStatus;
import com.github.makewheels.video2022.redis.CacheService;
import com.github.makewheels.video2022.transcode.TranscodeLauncher;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.utils.Environment;
import com.github.makewheels.video2022.utils.PathUtil;
import com.github.makewheels.video2022.video.bean.CreateVideoDTO;
import com.github.makewheels.video2022.video.bean.Video;
import com.github.makewheels.video2022.video.bean.VideoDetailVO;
import com.github.makewheels.video2022.video.bean.VideoSimpleVO;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import com.github.makewheels.video2022.video.constants.VideoType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class VideoService {
    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private YoutubeService youtubeService;
    @Resource
    private FileService fileService;
    @Resource
    private TranscodeLauncher transcodeLauncher;
    @Resource
    private CoverLauncher coverLauncher;

    @Resource
    private VideoRepository videoRepository;
    @Resource
    private CacheService cacheService;

    @Value("${internal-base-url}")
    private String internalBaseUrl;
    @Value("${short-url-service}")
    private String shortUrlService;

    @Value("${spring.profile.active}")
    private String environment;

    private String getWatchId() {
        return IdUtil.getSnowflakeNextIdStr();
    }

    private String getShortUrl(String fullUrl) {
        JSONObject body = new JSONObject();
        body.put("fullUrl", fullUrl);
        body.put("sign", "DuouXm25hwFWVbUmyw3a");
        String response = HttpUtil.post(shortUrlService, body.toJSONString());
        log.info("getShortUrl: body = {}, response = {}",
                JSON.toJSONString(body), JSON.toJSONString(response));
        return response;
    }

    /**
     * 创建新视频
     */
    public JSONObject create(CreateVideoDTO createVideoDTO) {
        User user = UserHolder.get();
        createVideoDTO.setUser(user);
        String userId = user.getId();

        Video video = new Video();
        createVideoDTO.setVideo(video);

        String videoType = createVideoDTO.getVideoType();
        video.setType(videoType);

        //YouTube
        if (videoType.equals(VideoType.YOUTUBE)) {
            String youtubeUrl = createVideoDTO.getYoutubeUrl();
            video.setYoutubeUrl(youtubeUrl);
            video.setYoutubeVideoId(youtubeService.getYoutubeVideoIdByUrl(youtubeUrl));
        }

        //创建 video file
        File videoFile = fileService.createVideoFile(createVideoDTO);

        String fileId = videoFile.getId();
        //创建 video
        video.setWatchCount(0);
        video.setOriginalFileId(fileId);
        video.setUserId(userId);
        String watchId = getWatchId();
        video.setWatchId(watchId);
        String watchUrl = internalBaseUrl + "/watch?v=" + watchId;
        video.setWatchUrl(watchUrl);

        //本地开发环境shortUrl就是watchUrl
        video.setShortUrl(watchUrl);

        if (environment.equals(Environment.PRODUCTION)) {
            String shortUrl = getShortUrl(watchUrl);
            video.setShortUrl(shortUrl);
        }

        //视频过期时间
        long expireTimeInMillis = Duration.ofDays(30).toMillis();
        Date expireTime = new Date(System.currentTimeMillis() + expireTimeInMillis);
        video.setExpireTime(expireTime);
        video.setIsPermanent(false);
        video.setIsOriginalFileDeleted(false);
        video.setIsTranscodeFilesDeleted(false);
        video.setUpdateTime(new Date());
        mongoTemplate.save(video);

        String videoId = video.getId();
        videoFile.setVideoId(videoId);
        // 更新file上传路径
        videoFile.setKey(PathUtil.getS3VideoPrefix(userId, videoId)
                + "/original/" + videoId + "." + videoFile.getExtension());
        mongoTemplate.save(videoFile);
        log.info("新建文件：" + JSON.toJSONString(videoFile));

        //更新video的source key
        video.setOriginalFileKey(videoFile.getKey());
        video.setUpdateTime(new Date());
        mongoTemplate.save(video);
        log.info("新建视频：" + JSON.toJSONString(video));

        //如果是搬运YouTube视频，多一个步骤，通知海外服务器
        if (videoType.equals(VideoType.YOUTUBE)) {
            new Thread(() -> handleCreateYoutube(video, user, videoFile)).start();
        }

        JSONObject response = new JSONObject();
        response.put("fileId", fileId);
        response.put("videoId", videoId);
        response.put("watchId", watchId);
        response.put("watchUrl", watchUrl);
        response.put("shortUrl", video.getShortUrl());
        return response;
    }

    /**
     * 创建视频：处理youtube
     */
    private void handleCreateYoutube(Video video, User user, File videoFile) {
        String extension = youtubeService.getFileExtension(video.getYoutubeVideoId());
        if (!videoFile.getExtension().equals(extension)) {
            //更新file
            videoFile.setExtension(extension);
            String newKey = videoFile.getKey();
            videoFile.setKey(newKey.substring(0, newKey.lastIndexOf(".")) + "." + extension);
            mongoTemplate.save(videoFile);
            //更新video的source key
            video.setOriginalFileKey(videoFile.getKey());
            video.setUpdateTime(new Date());
            mongoTemplate.save(video);
        }

        //提交搬运视频任务给海外服务器
        youtubeService.transferVideo(user, video, videoFile);

        //获取视频信息，保存title和description到数据库
        JSONObject youtubeVideoInfo = youtubeService.getVideoInfo(video.getYoutubeVideoId());
        video.setYoutubeVideoInfo(youtubeVideoInfo);

        //youtube视频可以直接发起搬运封面，不必等源视频上传完成
        //上传完成的截帧操作已做幂等性处理，会判断如果是youtube视频，跳过截帧
        new Thread(() -> coverLauncher.createCover(user, video)).start();

        //更新youtube publish time
        JSONObject publishedAt = youtubeVideoInfo.getJSONObject("snippet")
                .getJSONObject("publishedAt");
        int timeZoneShift = publishedAt.getInteger("timeZoneShift");
        long value = publishedAt.getLong("value");
        ZoneId zoneId = ZoneId.of("UTC+" + timeZoneShift);
        Instant instant = ZonedDateTime.ofInstant(Instant.ofEpochMilli(value), zoneId).toInstant();
        Date youtubePublishTime = Date.from(
                ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).toInstant());
        video.setYoutubePublishTime(youtubePublishTime);

        JSONObject snippet = youtubeVideoInfo.getJSONObject("snippet");
        video.setTitle(snippet.getString("title"));
        video.setDescription(snippet.getString("description"));
        video.setUpdateTime(new Date());
        mongoTemplate.save(video);
    }


    /**
     * 原始文件上传完成，开始转码
     */
    public void originalFileUploadFinish(String videoId) {
        User user = UserHolder.get();
        //查数据库，找到video
        Video video = videoRepository.getById(videoId);

        //校验
        if (video == null) throw new VideoException(ErrorCode.VIDEO_NOT_EXIST);

        File file = mongoTemplate.findById(video.getOriginalFileId(), File.class);
        if (file == null) throw new VideoException(ErrorCode.FILE_NOT_EXIST);

        if (!file.getStatus().equals(FileStatus.READY)) throw new VideoException(ErrorCode.FILE_NOT_READY);

        //更新视频为正在转码状态
        video.setStatus(VideoStatus.TRANSCODING);
        video.setUpdateTime(new Date());
        mongoTemplate.save(video);

        //创建子线程发起转码，先给前端返回结果
        new Thread(() -> transcodeLauncher.transcodeVideo(user, video)).start();
        //封面：如果是youtube视频，之前创建的时候已经搬运封面了，用户上传视频要截帧
        if (!video.isYoutube()) {
            new Thread(() -> coverLauncher.createCover(user, video)).start();
        }
    }

    /**
     * 更新视频信息
     */
    public void updateVideo(Video newVideo) {
        User user = UserHolder.get();
        String userId = user.getId();
        String videoId = newVideo.getId();
        Video oldVideo = videoRepository.getById(videoId);
        //判断视频是否存在
        if (oldVideo == null) {
            throw new VideoException(ErrorCode.VIDEO_NOT_EXIST);
        }
        //判断视频是否属于当前用户
        if (!StringUtils.equals(userId, oldVideo.getUserId())) {
            throw new VideoException(ErrorCode.FAIL);
        }

        oldVideo.setTitle(newVideo.getTitle());
        oldVideo.setDescription(newVideo.getDescription());
        oldVideo.setUpdateTime(new Date());
        mongoTemplate.save(oldVideo);

        log.info("更新视频信息：videoId = {}, title = {}, description = {}",
                videoId, oldVideo.getTitle(), oldVideo.getDescription());
    }

    /**
     * 获取视频详情
     */
    public VideoDetailVO getVideoDetail(String videoId) {
        Video video = cacheService.getVideo(videoId);
        if (video == null) {
            throw new VideoException(ErrorCode.VIDEO_NOT_EXIST);
        }
        VideoDetailVO videoDetailVO = new VideoDetailVO();
        BeanUtils.copyProperties(video, videoDetailVO);
        videoDetailVO.setCreateTimeString(DateUtil.formatDateTime(video.getCreateTime()));
        videoDetailVO.setYoutubePublishTimeString(DateUtil.formatDateTime(video.getYoutubePublishTime()));
        return videoDetailVO;
    }

    /**
     * 分页获取我的视频列表
     */
    public List<VideoSimpleVO> getMyVideoList(int skip, int limit) {
        String userId = UserHolder.get().getId();
        return getVideoList(userId, skip, limit);
    }

    /**
     * 分页获取指定userId视频列表
     */
    private List<VideoSimpleVO> getVideoList(String userId, int skip, int limit) {
        List<Video> videos = videoRepository.getVideosByUserId(userId, skip, limit);

        List<VideoSimpleVO> itemList = new ArrayList<>(videos.size());
        for (Video video : videos) {
            VideoSimpleVO item = new VideoSimpleVO();
            BeanUtils.copyProperties(video, item);
            item.setCreateTimeString(DateUtil.formatDateTime(video.getCreateTime()));
            item.setYoutubePublishTimeString(DateUtil.formatDateTime(video.getYoutubePublishTime()));
            itemList.add(item);
        }
        return itemList;
    }

    /**
     * 获取过期视频
     */
    public List<Video> getExpiredVideos(int skip, int limit) {
        return videoRepository.getExpiredVideos(skip, limit);
    }

    /**
     * 获取原始文件下载地址
     */
    public String getOriginalFileDownloadUrl(String videoId) {
        Video video = videoRepository.getById(videoId);
        String originalFileKey = video.getOriginalFileKey();
        return fileService.generatePresignedUrl(originalFileKey, Duration.ofHours(2));
    }
}
