package com.github.makewheels.video2022.video;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.cover.Cover;
import com.github.makewheels.video2022.cover.CoverLauncher;
import com.github.makewheels.video2022.cover.CoverRepository;
import com.github.makewheels.video2022.etc.ip.IpService;
import com.github.makewheels.video2022.etc.response.ErrorCode;
import com.github.makewheels.video2022.etc.response.Result;
import com.github.makewheels.video2022.file.File;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.file.constants.FileStatus;
import com.github.makewheels.video2022.file.constants.S3Provider;
import com.github.makewheels.video2022.transcode.Transcode;
import com.github.makewheels.video2022.transcode.TranscodeLauncher;
import com.github.makewheels.video2022.transcode.TranscodeRepository;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.utils.Environment;
import com.github.makewheels.video2022.utils.PathUtil;
import com.github.makewheels.video2022.video.bean.Video;
import com.github.makewheels.video2022.video.bean.VideoDetail;
import com.github.makewheels.video2022.video.bean.VideoSimpleInfoVO;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import com.github.makewheels.video2022.video.constants.VideoType;
import com.github.makewheels.video2022.watch.watchinfo.PlayUrl;
import com.github.makewheels.video2022.watch.watchinfo.WatchInfo;
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
    private VideoRedisService videoRedisService;
    @Resource
    private YoutubeService youtubeService;
    @Resource
    private FileService fileService;
    @Resource
    private TranscodeLauncher transcodeLauncher;
    @Resource
    private CoverLauncher coverLauncher;
    @Resource
    private IpService ipService;

    @Resource
    private VideoRepository videoRepository;
    @Resource
    private CoverRepository coverRepository;
    @Resource
    private TranscodeRepository transcodeRepository;

    @Value("${internal-base-url}")
    private String internalBaseUrl;
    @Value("${short-url-service}")
    private String shortUrlService;

    @Value("${spring.profile.active}")
    private String environment;

    private String getWatchId() {
//        String json = HttpUtil.get("https://service-d5xe9zbh-1253319037.bj.apigw.tencentcs.com/release/");
//        JSONObject jsonObject = JSONObject.parseObject(json);
//        return jsonObject.getJSONObject("data").getString("prettyId");
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
    public Result<JSONObject> create(User user, JSONObject body) {
        String userId = user.getId();
        Video video = new Video();
        String videoType = body.getString("type");
        video.setType(videoType);
        if (videoType.equals(VideoType.YOUTUBE)) {
            String youtubeUrl = body.getString("youtubeUrl");
            video.setYoutubeUrl(youtubeUrl);
            video.setYoutubeVideoId(youtubeService.getYoutubeVideoIdByUrl(youtubeUrl));
        }
        video.setProvider(S3Provider.ALIYUN_OSS);

        //创建 video file
        File videoFile = fileService.createVideoFile(user, video, body);

        String fileId = videoFile.getId();
        //创建 video
        video.setWatchCount(0);
        video.setOriginalFileId(fileId);
        video.setUserId(userId);
        String watchId = getWatchId();
        video.setWatchId(watchId);
        String watchUrl = internalBaseUrl + "/watch?v=" + watchId;
        video.setWatchUrl(watchUrl);
        String shortUrl = null;
        if (environment.equals(Environment.PRODUCTION)) {
            shortUrl = getShortUrl(watchUrl);
            video.setShortUrl(shortUrl);
        } else {
            video.setShortUrl(watchUrl);
        }
        video.setStatus(VideoStatus.CREATED);
        video.setCreateTime(new Date());
        video.setUpdateTime(new Date());

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
        response.put("shortUrl", shortUrl);
        return Result.ok(response);
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
        JSONObject publishedAt = youtubeVideoInfo.getJSONObject("snippet").getJSONObject("publishedAt");
        int timeZoneShift = publishedAt.getInteger("timeZoneShift");
        long value = publishedAt.getLong("value");
        ZoneId zoneId = ZoneId.of("UTC+" + timeZoneShift);
        Instant instant = ZonedDateTime.ofInstant(Instant.ofEpochMilli(value), zoneId).toInstant();
        Date youtubePublishTime = Date.from(ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).toInstant());
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
    public Result<Void> originalFileUploadFinish(User user, String videoId) {
        //查数据库，找到video
        Video video = mongoTemplate.findById(videoId, Video.class);

        //校验文件
        if (video == null) return Result.error(ErrorCode.FAIL);
        File file = mongoTemplate.findById(video.getOriginalFileId(), File.class);
        if (file == null) return Result.error(ErrorCode.FAIL);
        if (!file.getStatus().equals(FileStatus.READY)) return Result.error(ErrorCode.FAIL);

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
        return Result.ok();
    }

    /**
     * 更新视频信息
     */
    public Result<Void> updateVideo(User user, Video newVideo) {
        String userId = user.getId();
        String videoId = newVideo.getId();
        Video oldVideo = mongoTemplate.findById(videoId, Video.class);
        //判断视频是否存在，判断视频是否属于当前用户
        if (oldVideo == null || !StringUtils.equals(userId, oldVideo.getUserId())) {
            return Result.error(ErrorCode.FAIL);
        }
        oldVideo.setTitle(newVideo.getTitle());
        oldVideo.setDescription(newVideo.getDescription());
        oldVideo.setUpdateTime(new Date());
        mongoTemplate.save(oldVideo);
        log.info("更新视频信息：videoId = {}, title = {}, description = {}",
                videoId, oldVideo.getTitle(), oldVideo.getDescription());
        return Result.ok();
    }

    /**
     * 获取播放信息
     */
    public Result<WatchInfo> getWatchInfo(User user, String watchId, String clientId, String sessionId) {
        WatchInfo watchInfo = videoRedisService.getWatchInfo(watchId);
        //如果已经存在缓存，直接返回
        if (watchInfo != null) {
            return Result.ok(watchInfo);
        }
        //如果没有缓存，查数据库，缓存，返回
        Video video = videoRepository.getByWatchId(watchId);
        if (video == null) {
            log.error("查不到这个video, watchId = " + watchId);
            return Result.error(ErrorCode.FAIL);
        }
        String videoId = video.getId();
        watchInfo = new WatchInfo();
        watchInfo.setVideoId(videoId);
        //通过videoId查找封面
        Cover cover = coverRepository.getByVideoId(videoId);
        if (cover != null) {
            watchInfo.setCoverUrl(cover.getAccessUrl());
        }

        //拿m3u8播放地址
        List<Transcode> transcodeList;
        List<String> transcodeIds = video.getTranscodeIds();
        transcodeList = transcodeRepository.getByIds(transcodeIds);

        //排序，transcodeList里，1080p分辨率放前面
//        if (transcodeList.size() >= 2 && transcodeList.get(0).getResolution().equals(Resolution.R_720P)) {
//            Collections.reverse(transcodeList);
//        }

        List<PlayUrl> playUrlList = new ArrayList<>(transcodeList.size());
        for (Transcode transcode : transcodeList) {
            PlayUrl playUrl = new PlayUrl();
            String resolution = transcode.getResolution();
            playUrl.setResolution(resolution);
            //改成，调用我自己的getM3u8Content接口，获取m3u8内容
            playUrl.setUrl(internalBaseUrl + "/watchController/getM3u8Content.m3u8?"
                    + "videoId=" + videoId
                    + "&clientId=" + clientId
                    + "&sessionId=" + sessionId
                    + "&transcodeId=" + transcode.getId()
                    + "&resolution=" + resolution
            );

            playUrlList.add(playUrl);
        }
        watchInfo.setPlayUrlList(playUrlList);
        watchInfo.setVideoStatus(video.getStatus());

        //缓存redis，先判断视频状态：只有READY才放入缓存
        if (video.isReady()) {
//            videoRedisService.setWatchInfo(watchId, watchInfo);
        }
        return Result.ok(watchInfo);
    }

    /**
     * 获取视频详情
     */
    public Result<VideoDetail> getVideoDetail(User user, String videoId) {
        Video video = mongoTemplate.findById(videoId, Video.class);
        if (video == null) {
            return Result.error(ErrorCode.FAIL);
        }
        VideoDetail videoDetail = new VideoDetail();
        BeanUtils.copyProperties(video, videoDetail);
        videoDetail.setCreateTimeString(DateUtil.formatDateTime(video.getCreateTime()));
        videoDetail.setYoutubePublishTimeString(DateUtil.formatDateTime(video.getYoutubePublishTime()));
        return Result.ok(videoDetail);
    }

    /**
     * 分页获取指定userId视频列表
     */
    public Result<List<VideoSimpleInfoVO>> getVideoList(String userId, int skip, int limit) {
        List<Video> videos = videoRepository.getVideosByUserId(userId, skip, limit);

        List<VideoSimpleInfoVO> itemList = new ArrayList<>(videos.size());
        videos.forEach(video -> {
            VideoSimpleInfoVO item = new VideoSimpleInfoVO();
            BeanUtils.copyProperties(video, item);
            item.setCreateTimeString(DateUtil.formatDateTime(video.getCreateTime()));
            item.setYoutubePublishTimeString(DateUtil.formatDateTime(video.getYoutubePublishTime()));
            itemList.add(item);
        });
        return Result.ok(itemList);
    }

    /**
     * 获取过期视频
     */
    public List<Video> getExpiredVideos(int skip, int limit) {
        return videoRepository.getExpiredVideos(skip, limit);
    }

}
