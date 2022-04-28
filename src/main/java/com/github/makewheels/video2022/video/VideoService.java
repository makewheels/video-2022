package com.github.makewheels.video2022.video;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.usermicroservice2022.user.User;
import com.github.makewheels.video2022.cover.Cover;
import com.github.makewheels.video2022.cover.CoverLauncher;
import com.github.makewheels.video2022.cover.CoverRepository;
import com.github.makewheels.video2022.file.File;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.file.FileStatus;
import com.github.makewheels.video2022.file.S3Provider;
import com.github.makewheels.video2022.response.ErrorCode;
import com.github.makewheels.video2022.response.Result;
import com.github.makewheels.video2022.transcode.Transcode;
import com.github.makewheels.video2022.transcode.TranscodeLauncher;
import com.github.makewheels.video2022.transcode.TranscodeRepository;
import com.github.makewheels.video2022.utils.PathUtil;
import com.github.makewheels.video2022.video.bean.Video;
import com.github.makewheels.video2022.video.bean.VideoDetail;
import com.github.makewheels.video2022.video.bean.VideoSimpleInfo;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import com.github.makewheels.video2022.video.constants.VideoType;
import com.github.makewheels.video2022.watch.WatchLog;
import com.github.makewheels.video2022.watch.WatchRepository;
import com.github.makewheels.video2022.watch.watchinfo.PlayUrl;
import com.github.makewheels.video2022.watch.watchinfo.WatchInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
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
    private VideoRepository videoRepository;
    @Resource
    private CoverRepository coverRepository;
    @Resource
    private TranscodeRepository transcodeRepository;
    @Resource
    private WatchRepository watchRepository;

    @Value("${internal-base-url}")
    private String internalBaseUrl;
    @Value("${short-url-service}")
    private String shortUrlService;

    @Value("${baidu.bos.accessBaseUrl}")
    private String baiduBosAccessBaseUrl;
    @Value("${baidu.bos.cdnBaseUrl}")
    private String baiduBosCdnBaseUrl;
    @Value("${aliyun.oss.accessBaseUrl}")
    private String aliyunOssAccessBaseUrl;
    @Value("${aliyun.oss.cdnBaseUrl}")
    private String aliyunOssCdnBaseUrl;

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
        return HttpUtil.post(shortUrlService, body.toJSONString());
    }

    /**
     * 创建新视频
     */
    public Result<JSONObject> create(User user, JSONObject body) {
        String userId = user.getId();
        Video video = new Video();
        //决定S3提供商是阿里云还是百度云
        //现在为了兼容上传网页，用户上传继续用百度云
        //搬运因为是海外服务器api上传，就用阿里云对象存储，转码也是阿里云

        //视频的provider和file的provider是一回事，视频和源文件是一对一关系
        //但是transcode的provider可能和video的不一样
        // 不一定文件上传到阿里云对象存储，就用阿里云的转码，也可能用我自建的云函数
        String provider = null;
        String videoType = body.getString("type");
        video.setType(videoType);
        if (videoType.equals(VideoType.USER_UPLOAD)) {
            provider = S3Provider.ALIYUN_OSS;
        } else if (videoType.equals(VideoType.YOUTUBE)) {
            provider = S3Provider.ALIYUN_OSS;
            String youtubeUrl = body.getString("youtubeUrl");
            video.setYoutubeUrl(youtubeUrl);
            video.setYoutubeVideoId(youtubeService.getYoutubeVideoIdByUrl(youtubeUrl));
        }
        log.info("新建视频类型：type = " + videoType + ", S3Provider = " + provider);
        video.setProvider(provider);

        //创建 video file
        File videoFile = fileService.createVideoFile(user, provider, body);

        String fileId = videoFile.getId();
        //创建 video
        video.setWatchCount(0);
        video.setOriginalFileId(fileId);
        video.setUserId(userId);
        String watchId = getWatchId();
        video.setWatchId(watchId);
        String watchUrl = internalBaseUrl + "/watch?v=" + watchId;
        video.setWatchUrl(watchUrl);
        String shortUrl = getShortUrl(watchUrl);
        video.setShortUrl(shortUrl);
        video.setStatus(VideoStatus.CREATED);
        video.setCreateTime(new Date());
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
        mongoTemplate.save(video);
        log.info("新建视频：" + JSON.toJSONString(video));

        //如果是搬运YouTube视频，多一个步骤，通知海外服务器
        if (videoType.equals(VideoType.YOUTUBE)) {
            new Thread(() -> handleCreateYoutube(video, user, videoFile)).start();
        }

        JSONObject response = new JSONObject();
        response.put("fileId", fileId);
        response.put("videoId", video);
        response.put("watchId", watchId);
        response.put("watchUrl", watchUrl);
        response.put("shortUrl", shortUrl);
        return Result.ok(response);
    }

    /**
     * 创建视频：处理youtube
     *
     * @param video
     * @param user
     * @param videoFile
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
            mongoTemplate.save(video);
        }
        //提交任务给海外服务器
        youtubeService.transferVideo(user, video, videoFile);

        //获取视频信息，保存title和description到数据库
        JSONObject youtubeVideoInfo = youtubeService.getVideoInfo(video.getYoutubeVideoId());
        video.setYoutubeVideoInfo(youtubeVideoInfo);

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
        mongoTemplate.save(video);
    }


    /**
     * 原始文件上传完成，开始转码
     *
     * @param user
     * @param videoId
     * @return
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
        mongoTemplate.save(video);

        //创建子线程发起转码，先给前端返回结果
        new Thread(() -> transcodeLauncher.transcodeVideo(user, video)).start();
        //封面
        new Thread(() -> coverLauncher.createCover(user, video)).start();
        return Result.ok();
    }

    /**
     * 更新视频信息
     *
     * @param user
     * @param updateVideo
     * @return
     */
    public Result<Void> updateVideo(User user, Video updateVideo) {
        String userId = user.getId();
        String videoId = updateVideo.getId();
        Video oldVideo = mongoTemplate.findById(videoId, Video.class);
        //判断视频是否存在，判断视频是否属于当前用户
        if (oldVideo == null || !StringUtils.equals(userId, oldVideo.getUserId())) {
            return Result.error(ErrorCode.FAIL);
        }
        oldVideo.setTitle(updateVideo.getTitle());
        oldVideo.setDescription(updateVideo.getDescription());
        mongoTemplate.save(oldVideo);
        return Result.ok();
    }

    /**
     * 获取播放信息
     *
     * @param user
     * @param watchId
     * @return
     */
    public Result<WatchInfo> getWatchInfo(User user, String watchId) {
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
            watchInfo.setCoverUrl(cover.getCdnUrl());
        }
        //通过videoId查找m3u8播放地址
        List<Transcode> transcodeList = transcodeRepository.getByVideoId(videoId);
        List<PlayUrl> playUrlList = new ArrayList<>(transcodeList.size());
        for (Transcode transcode : transcodeList) {
            PlayUrl playUrl = new PlayUrl();
            playUrl.setResolution(transcode.getResolution());
            playUrl.setUrl(transcode.getM3u8CdnUrl());
            playUrlList.add(playUrl);
        }
        watchInfo.setPlayUrlList(playUrlList);
        watchInfo.setVideoStatus(video.getStatus());

        //缓存redis，先判断视频状态：只有READY才放入缓存
        if (video.getStatus().equals(VideoStatus.READY)) {
            videoRedisService.setWatchInfo(watchId, watchInfo);
        }
        return Result.ok(watchInfo);
    }

    /**
     * 获取视频详情
     *
     * @param user
     * @param videoId
     * @return
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
     *
     * @param userId
     * @param skip
     * @param limit
     * @return
     */
    public Result<List<VideoSimpleInfo>> getVideoList(String userId, int skip, int limit) {
        List<Video> videos = videoRepository.getVideoList(userId, skip, limit);

        List<VideoSimpleInfo> itemList = new ArrayList<>(videos.size());
        videos.forEach(video -> {
            VideoSimpleInfo item = new VideoSimpleInfo();
            BeanUtils.copyProperties(video, item);
            item.setCreateTimeString(DateUtil.formatDateTime(video.getCreateTime()));
            item.setYoutubePublishTimeString(DateUtil.formatDateTime(video.getYoutubePublishTime()));
            itemList.add(item);
        });
        return Result.ok(itemList);
    }

    /**
     * 增加观看记录
     */
    public Result<Void> addWatchLog(HttpServletRequest request, User user, String clientId,
                                    String sessionId, String videoId) {
        //观看记录根据videoId和sessionId判断是否已存在观看记录，如果已存在则跳过
        if (watchRepository.isWatchLogExist(videoId, sessionId)) {
            return Result.ok();
        }
        //保存观看记录
        WatchLog watchLog = new WatchLog();
        watchLog.setIp(request.getRemoteAddr());
        watchLog.setUserAgent(request.getHeader("User-Agent"));

        watchLog.setVideoId(videoId);
        watchLog.setClientId(clientId);
        watchLog.setSessionId(sessionId);

        mongoTemplate.save(watchLog);
        //增加video观看次数
        videoRepository.addWatchCount(videoId);
        return Result.ok();
    }

    /**
     * 获取视频信息
     */
    public Result<JSONObject> getYoutubeVideoInfo(JSONObject body) {
        String youtubeUrl = body.getString("youtubeUrl");
        String youtubeVideoId = youtubeService.getYoutubeVideoIdByUrl(youtubeUrl);
        JSONObject videoInfo = youtubeService.getVideoInfo(youtubeVideoId);
        return Result.ok(videoInfo);
    }
}
