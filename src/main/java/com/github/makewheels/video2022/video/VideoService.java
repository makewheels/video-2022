package com.github.makewheels.video2022.video;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.mts20140618.models.SubmitJobsResponseBody;
import com.aliyun.mts20140618.models.SubmitMediaInfoJobResponseBody;
import com.baidubce.services.media.model.CreateThumbnailJobResponse;
import com.baidubce.services.media.model.CreateTranscodingJobResponse;
import com.baidubce.services.media.model.GetMediaInfoOfFileResponse;
import com.github.makewheels.usermicroservice2022.User;
import com.github.makewheels.usermicroservice2022.response.ErrorCode;
import com.github.makewheels.video2022.file.File;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.file.FileStatus;
import com.github.makewheels.video2022.response.Result;
import com.github.makewheels.video2022.thumbnail.Thumbnail;
import com.github.makewheels.video2022.thumbnail.ThumbnailRepository;
import com.github.makewheels.video2022.thumbnail.ThumbnailService;
import com.github.makewheels.video2022.transcode.*;
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
    private FileService fileService;
    @Resource
    private TranscodeService transcodeService;
    @Resource
    private ThumbnailService thumbnailService;

    @Resource
    private VideoRepository videoRepository;
    @Resource
    private ThumbnailRepository thumbnailRepository;
    @Resource
    private TranscodeRepository transcodeRepository;
    @Resource
    private WatchRepository watchRepository;

    @Resource
    private VideoRedisService videoRedisService;
    @Resource
    private YoutubeService youtubeService;

    @Resource
    private AliyunMpsService aliyunMpsService;
    @Resource
    private BaiduMcpService baiduMcpService;

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

    public Result<JSONObject> create(User user, JSONObject requestBody) {
        String userId = user.getId();
        Video video = new Video();

        //决定提供商是阿里云还是百度云
        //现在为了兼容上传网页，用户上传继续用百度云
        //搬运因为是海外服务器api上传，就用阿里云对象存储，转码也是阿里云
        String provider = null;
        String type = requestBody.getString("type");
        video.setType(type);
        if (type.equals(VideoType.USER_UPLOAD)) {
            provider = S3Provider.BAIDU_BOS;
            log.info("新建视频类型：type = " + type + ", provider = 百度云");
        } else if (type.equals(VideoType.YOUTUBE)) {
            provider = S3Provider.ALIYUN_OSS;
            log.info("新建视频类型：type = " + type + ", provider = 阿里云");
            String youtubeUrl = requestBody.getString("youtubeUrl");
            video.setYoutubeUrl(youtubeUrl);
            video.setYoutubeVideoId(youtubeService.getYoutubeVideoId(youtubeUrl));
        }
        video.setProvider(provider);

        //创建 file
        File file = fileService.create(user, provider, requestBody);

        String fileId = file.getId();
        //创建 video
        video.setWatchCount(0);
        video.setOriginalFileId(fileId);
        video.setUserId(userId);
        String watchId = getWatchId();
        video.setWatchId(watchId);
        String watchUrl = internalBaseUrl + "/watch?v=" + watchId;
        video.setWatchUrl(watchUrl);
        video.setShortUrl(getShortUrl(watchUrl));
        video.setStatus(VideoStatus.CREATED);
        video.setCreateTime(new Date());
        mongoTemplate.save(video);

        String videoId = video.getId();
        file.setVideoId(videoId);
        // 更新file上传路径
        String key = "videos/" + userId + "/" + videoId + "/original/" + videoId + "." + file.getExtension();
        file.setKey(key);
        mongoTemplate.save(file);
        log.info("新建文件：" + JSON.toJSONString(file));

        //更新video的source key
        video.setOriginalFileKey(key);
        mongoTemplate.save(video);
        log.info("新建视频：" + JSON.toJSONString(video));

        //如果是搬运YouTube视频，多一个步骤，通知海外服务器
        if (type.equals(VideoType.YOUTUBE)) {
            new Thread(() -> {
                //因为海外服务器获取文件拓展名很慢，所以放到这里，在子线程中执行，先给前端放回结果
                //之前已保存到数据库的file和video的sourceKey有可能需要更新
                //YouTube搬运视频没有源文件名，只有拓展名，是yt-dlp给的，之后上传的key也会用这个拓展名
                String youtubeUrl = requestBody.getString("youtubeUrl");
                String youtubeVideoId = youtubeService.getYoutubeVideoId(youtubeUrl);
                String extension = youtubeService.getFileExtension(youtubeVideoId);
                if (!file.getExtension().equals(extension)) {
                    //更新file
                    file.setExtension(extension);
                    file.setKey(FileNameUtil.mainName(file.getKey() + "." + extension));
                    mongoTemplate.save(file);
                    //更新video的source key
                    video.setOriginalFileKey(file.getKey());
                    mongoTemplate.save(video);
                }
                //提交任务给海外服务器
                youtubeService.submitMission(user, video, file);
                //获取视频信息，保存title和description到数据库
                JSONObject jsonObject = youtubeService.getVideoInfo(video);
                video.setYoutubeVideoInfo(jsonObject);
                JSONObject snippet = jsonObject.getJSONObject("snippet");
                video.setTitle(snippet.getString("title"));
                video.setDescription(snippet.getString("description"));
                mongoTemplate.save(video);
            }).start();
        }

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("fileId", fileId);
        jsonObject.put("videoId", video.getId());
        jsonObject.put("watchId", video.getWatchId());
        jsonObject.put("watchUrl", video.getWatchUrl());
        jsonObject.put("shortUrl", video.getShortUrl());
        return Result.ok(jsonObject);
    }

    /**
     * 转码单分辨率
     *
     * @param user
     * @param video
     * @param resolution
     */
    private void transcodeSingleResolution(User user, Video video, String resolution) {
        String userId = user.getId();
        String videoId = video.getId();
        String provider = video.getProvider();
        String sourceKey = video.getOriginalFileKey();

        //新建transcode对象，保存到数据库
        Transcode transcode = new Transcode();
        transcode.setUserId(userId);
        transcode.setVideoId(videoId);
        transcode.setProvider(provider);
        transcode.setCreateTime(new Date());
        //已创建状态，反正后面马上就要再次请求更新状态，所以这里就先保存CREATED
        transcode.setStatus(BaiduTranscodeStatus.CREATED);
        transcode.setResolution(resolution);
        transcode.setSourceKey(sourceKey);
        String m3u8Key = "videos/" + userId + "/" + videoId + "/transcode/"
                + resolution + "/" + videoId + ".m3u8";
        transcode.setM3u8Key(m3u8Key);
        if (provider.equals(S3Provider.ALIYUN_OSS)) {
            transcode.setM3u8AccessUrl(aliyunOssAccessBaseUrl + m3u8Key);
            transcode.setM3u8CdnUrl(aliyunOssCdnBaseUrl + m3u8Key);
        } else if (provider.equals(S3Provider.BAIDU_BOS)) {
            transcode.setM3u8AccessUrl(baiduBosAccessBaseUrl + m3u8Key);
            transcode.setM3u8CdnUrl(baiduBosCdnBaseUrl + m3u8Key);
        }
        mongoTemplate.save(transcode);

        //发起转码，保存jobId，更新jobStatus
        log.info("发起 " + resolution + " 转码：videoId = " + videoId);
        String jobId = null;
        String jobStatus = null;
        if (provider.equals(S3Provider.ALIYUN_OSS)) {
            SubmitJobsResponseBody.SubmitJobsResponseBodyJobResultListJobResultJob job
                    = aliyunMpsService.createTranscodingJobByResolution(sourceKey, m3u8Key, resolution)
                    .getBody().getJobResultList().getJobResult().get(0).getJob();
            jobId = job.getJobId();
            log.info("发起阿里云转码 jobId = " + jobId + ", response = " + JSON.toJSONString(job));
            jobStatus = job.getState();
        } else if (provider.equals(S3Provider.BAIDU_BOS)) {
            CreateTranscodingJobResponse job = baiduMcpService.createTranscodingJob(
                    sourceKey, m3u8Key, resolution);
            jobId = job.getJobId();
            log.info("发起百度云转码 jobId = " + jobId + ", response = " + JSON.toJSONString(job));
            jobStatus = baiduMcpService.getTranscodingJob(jobId).getJobStatus();
        }
        transcode.setJobId(jobId);
        transcode.setStatus(jobStatus);
        mongoTemplate.save(transcode);
        //异步轮询查询阿里云转码状态，并回调
        if (provider.equals(S3Provider.ALIYUN_OSS)) {
            new Thread(() -> transcodeService.iterateQueryAliyunTranscodeJob(video, transcode)).start();
        }
    }

    /**
     * 发起截帧任务
     */
    private void createThumbnail(User user, Video video) {
        String userId = user.getId();
        String videoId = video.getId();
        String sourceKey = video.getOriginalFileKey();
        String provider = video.getProvider();

        Thumbnail thumbnail = new Thumbnail();
        thumbnail.setCreateTime(new Date());
        thumbnail.setUserId(userId);
        thumbnail.setVideoId(videoId);
        thumbnail.setStatus(BaiduTranscodeStatus.CREATED);
        thumbnail.setSourceKey(sourceKey);
        thumbnail.setExtension("jpg");
        thumbnail.setProvider(S3Provider.BAIDU_BOS);

        String targetKeyPrefix = "videos/" + userId + "/" + videoId + "/cover/" + videoId;
        CreateThumbnailJobResponse thumbnailJob
                = thumbnailService.createThumbnailJob(sourceKey, targetKeyPrefix);
        log.info("通过百度云发起截帧任务：CreateThumbnailJobResponse = " + JSON.toJSONString(thumbnailJob));


        thumbnail.setTargetKeyPrefix(targetKeyPrefix);
        String key = targetKeyPrefix + ".jpg";
        thumbnail.setKey(key);
        thumbnail.setAccessUrl(baiduBosAccessBaseUrl + key);
        thumbnail.setCdnUrl(baiduBosCdnBaseUrl + key);

        String thumbnailJobId = thumbnailJob.getJobId();
        thumbnail.setJobId(thumbnailJobId);

        mongoTemplate.save(thumbnail);
        //再次查询，更新状态
        thumbnail.setStatus(thumbnailService.getThumbnailJob(thumbnailJobId).getJobStatus());
        mongoTemplate.save(thumbnail);
        //更新video的冗余字段coverUrl
        video.setCoverUrl(thumbnail.getCdnUrl());
        mongoTemplate.save(video);
    }

    /**
     * 开始发起对单个视频的转码
     */
    private void transcodeSingleVideo(User user, Video video) {
        String videoId = video.getId();

        String sourceKey = video.getOriginalFileKey();
        String provider = video.getProvider();

        //获取视频信息
        if (provider.equals(S3Provider.ALIYUN_OSS)) {
            log.info("视频源文件上传完成，通过阿里云获取视频信息，videoId = " + videoId);
            SubmitMediaInfoJobResponseBody body = aliyunMpsService.getMediaInfo(sourceKey).getBody();
            SubmitMediaInfoJobResponseBody.SubmitMediaInfoJobResponseBodyMediaInfoJob job = body.getMediaInfoJob();
            log.info("阿里云获取视频信息返回：" + JSON.toJSONString(job));
            //给video保存媒体信息到数据库
            video.setMediaInfo(JSONObject.parseObject(JSON.toJSONString(job)));
            String jobId = job.getJobId();
            log.info("获取视频信息 jobId = " + jobId);
            SubmitMediaInfoJobResponseBody.SubmitMediaInfoJobResponseBodyMediaInfoJobProperties properties
                    = job.getProperties();
            video.setDuration((int) (Double.parseDouble(properties.getDuration()) * 1000));
            video.setHeight(Integer.parseInt(properties.getHeight()));
            video.setWidth(Integer.parseInt(properties.getWidth()));
        } else if (provider.equals(S3Provider.BAIDU_BOS)) {
            GetMediaInfoOfFileResponse mediaInfo = baiduMcpService.getMediaInfo(sourceKey);
            log.info("视频源文件上传完成，通过百度获取视频信息，videoId = " + videoId
                    + ", mediaInfo = " + JSON.toJSONString(mediaInfo));
            //截帧
            createThumbnail(user, video);
            video.setDuration(mediaInfo.getDurationInMillisecond());
            video.setMediaInfo(JSONObject.parseObject(JSON.toJSONString(mediaInfo)));
            video.setWidth(mediaInfo.getVideo().getWidthInPixel());
            video.setHeight(mediaInfo.getVideo().getHeightInPixel());
        }
        video.setStatus(VideoStatus.TRANSCODING);
        mongoTemplate.save(video);

        //开始转码，首先一定会发起720p的转码
        transcodeSingleResolution(user, video, Resolution.R_720P);

        //如果宽高大于1280*720，再次发起1080p转码
        if ((video.getWidth() * video.getHeight()) > (1280 * 720)) {
            transcodeSingleResolution(user, video, Resolution.R_1080P);
        }
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
        if (!file.getStatus().equals(FileStatus.READY))
            return Result.error(ErrorCode.FAIL);

        //更新视频为正在转码状态
        video.setStatus(VideoStatus.TRANSCODING);
        mongoTemplate.save(video);

        //创建子线程执行转码任务，先给前端返回结果
        new Thread(() -> transcodeSingleVideo(user, video)).start();

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
        Thumbnail thumbnail = thumbnailRepository.getByVideoId(videoId);
        if (thumbnail != null) {
            watchInfo.setCoverUrl(thumbnail.getCdnUrl());
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
    public Result<VideoInfo> getVideoInfo(User user, String videoId) {
        Video video = mongoTemplate.findById(videoId, Video.class);
        if (video == null) {
            return Result.error(ErrorCode.FAIL);
        }
        VideoInfo videoInfo = new VideoInfo();
        BeanUtils.copyProperties(video, videoInfo);
        Date createTime = videoInfo.getCreateTime();
        //如果是YouTube搬运视频，那就按照YouTube的发布时间来，不按照我搬运的时间
        if (video.isYoutube()) {
            JSONObject youtubeVideoInfo = video.getYoutubeVideoInfo();
            JSONObject publishedAt = youtubeVideoInfo.getJSONObject("snippet").getJSONObject("publishedAt");
            int timeZoneShift = publishedAt.getInteger("timeZoneShift");
            long value = publishedAt.getLong("value");
            Instant instant = ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(value), ZoneId.of("UTC+" + timeZoneShift)).toInstant();
            createTime = Date.from(ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).toInstant());
        }
        videoInfo.setCreateTimeString(DateUtil.formatDateTime(createTime));
        return Result.ok(videoInfo);
    }

    /**
     * 分页获取指定userId视频列表
     *
     * @param user
     * @param userId
     * @param skip
     * @param limit
     * @return
     */
    public Result<List<VideoInfo>> getVideoList(User user, String userId, int skip, int limit) {
        List<Video> videoList = videoRepository.getVideoList(userId, skip, limit);
        List<VideoInfo> videoInfoList = new ArrayList<>(videoList.size());
        for (Video video : videoList) {
            VideoInfo videoInfo = new VideoInfo();
            BeanUtils.copyProperties(video, videoInfo);
            videoInfoList.add(videoInfo);
        }
        return Result.ok(videoInfoList);
    }

    /**
     * 增加观看记录
     */
    public Result<Void> addWatchLog(
            HttpServletRequest request, User user,
            String clientId, String sessionId, String videoId) {
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

}
