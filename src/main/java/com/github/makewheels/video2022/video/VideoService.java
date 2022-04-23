package com.github.makewheels.video2022.video;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.mts20140618.models.SubmitJobsResponseBody;
import com.aliyun.mts20140618.models.SubmitMediaInfoJobResponseBody;
import com.baidubce.services.media.model.CreateThumbnailJobResponse;
import com.baidubce.services.media.model.CreateTranscodingJobResponse;
import com.baidubce.services.media.model.GetMediaInfoOfFileResponse;
import com.github.makewheels.usermicroservice2022.user.User;
import com.github.makewheels.video2022.file.File;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.file.FileStatus;
import com.github.makewheels.video2022.file.S3Provider;
import com.github.makewheels.video2022.response.ErrorCode;
import com.github.makewheels.video2022.response.Result;
import com.github.makewheels.video2022.thumbnail.Thumbnail;
import com.github.makewheels.video2022.thumbnail.ThumbnailRepository;
import com.github.makewheels.video2022.thumbnail.ThumbnailService;
import com.github.makewheels.video2022.transcode.*;
import com.github.makewheels.video2022.transcode.aliyun.AliyunMpsService;
import com.github.makewheels.video2022.transcode.baidu.BaiduMcpService;
import com.github.makewheels.video2022.transcode.baidu.BaiduTranscodeStatus;
import com.github.makewheels.video2022.transcode.cloudfunction.CloudFunctionTranscodeService;
import com.github.makewheels.video2022.video.constants.AudioCodec;
import com.github.makewheels.video2022.video.constants.VideoCodec;
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
    @Resource
    private CloudFunctionTranscodeService cloudFunctionTranscodeService;

    @Value("${internal-base-url}")
    private String internalBaseUrl;
    @Value("${external-base-url}")
    private String externalBaseUrl;
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

        //视频的provider和file的provider是一回事，视频和源文件是一对一关系
        //但是transcode的provider可能和video的不一样
        // 不一定文件上传到阿里云对象存储，就用阿里云的转码，也可能用我自建的云函数
        String provider = null;
        String type = requestBody.getString("type");
        video.setType(type);
        if (type.equals(VideoType.USER_UPLOAD)) {
            provider = S3Provider.BAIDU_BOS;
        } else if (type.equals(VideoType.YOUTUBE)) {
            provider = S3Provider.ALIYUN_OSS;
            String youtubeUrl = requestBody.getString("youtubeUrl");
            video.setYoutubeUrl(youtubeUrl);
            video.setYoutubeVideoId(youtubeService.getYoutubeVideoId(youtubeUrl));
        }
        log.info("新建视频类型：type = " + type + ", S3Provider = " + provider);

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
                    String newKey = file.getKey();
                    file.setKey(newKey.substring(0, newKey.lastIndexOf(".")) + "." + extension);
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

    private boolean isResolutionOverThan720p(int width, int height) {
        return width * height > 1280 * 720;
    }

    private boolean isResolutionOverThanTarget(int width, int height, String resolution) {
        if (resolution.equals(Resolution.R_720P)) {
            return width * height > 1280 * 720;
        } else if (resolution.equals(Resolution.R_1080P)) {
            return width * height > 1920 * 1080;
        }
        return false;
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
        String s3Provider = video.getProvider();
        String sourceKey = video.getOriginalFileKey();
        int width = video.getWidth();
        int height = video.getHeight();

        //新建transcode对象，保存到数据库
        Transcode transcode = new Transcode();
        transcode.setUserId(userId);
        transcode.setVideoId(videoId);
        transcode.setResolution(resolution);
        transcode.setSourceKey(sourceKey);
        transcode.setCreateTime(new Date());
        //已创建状态，反正后面马上就要再次请求更新状态，所以这里就先保存CREATED
        transcode.setStatus("CREATED");

        //这里问题来了：如何决定用谁转码？
        //如果不是h264，用阿里云。暂不考虑音频编码，仅音频不是aac也是我来转
        //如果是h264，源视频分辨率和目标分辨率不一致，用阿里云
        //其它情况用自建的阿里云 云函数
        String transcodeProvider;
        if (!VideoCodec.isH264(video.getVideoCodec())) {
            transcodeProvider = TranscodeProvider.getByS3Provider(s3Provider);
            //关于源视频和转码模板分辨率是否一致，我这样判断：
            //源片分辨率面积小于目标，就一致。大于目标，就不一致
            //说白了就是，往小了转就要编解码，往大了转（当然没这种情况）就源片，所以我云函数不改分辨率，正好
        } else if (isResolutionOverThanTarget(width, height, resolution)) {
            transcodeProvider = TranscodeProvider.getByS3Provider(s3Provider);
            //如果，是264，分辨率也不用改，但是是百度对象存储，那还得用百度，因为云函数只有阿里云
        } else if (video.getProvider().equals(S3Provider.BAIDU_BOS)) {
            transcodeProvider = TranscodeProvider.getByS3Provider(s3Provider);
        } else {
            //其它情况用阿里云 云函数
            transcodeProvider = TranscodeProvider.ALIYUN_CLOUD_FUNCTION;
        }
        transcode.setProvider(transcodeProvider);
        mongoTemplate.save(transcode);
        String transcodeId = transcode.getId();

        //设置m3u8 url
        String m3u8Key = "videos/" + userId + "/" + videoId + "/transcode/"
                + resolution + "/" + transcodeId + ".m3u8";
        transcode.setM3u8Key(m3u8Key);
        if (s3Provider.equals(S3Provider.ALIYUN_OSS)) {
            transcode.setM3u8AccessUrl(aliyunOssAccessBaseUrl + m3u8Key);
            transcode.setM3u8CdnUrl(aliyunOssCdnBaseUrl + m3u8Key);
        } else if (s3Provider.equals(S3Provider.BAIDU_BOS)) {
            transcode.setM3u8AccessUrl(baiduBosAccessBaseUrl + m3u8Key);
            transcode.setM3u8CdnUrl(baiduBosCdnBaseUrl + m3u8Key);
        }
        mongoTemplate.save(transcode);

        //发起转码
        log.info("发起 " + resolution + " 转码：videoId = " + videoId + ", transcode-provider = " + transcodeProvider);
        String jobId = null;
        String jobStatus = null;
        switch (transcodeProvider) {
            case TranscodeProvider.ALIYUN_MPS: {
                SubmitJobsResponseBody.SubmitJobsResponseBodyJobResultListJobResultJob job
                        = aliyunMpsService.createTranscodingJobByResolution(sourceKey, m3u8Key, resolution)
                        .getBody().getJobResultList().getJobResult().get(0).getJob();
                jobId = job.getJobId();
                log.info("发起阿里云转码 jobId = " + jobId + ", response = " + JSON.toJSONString(job));
                jobStatus = job.getState();
                break;
            }
            case TranscodeProvider.BAIDU_MCP: {
                CreateTranscodingJobResponse job = baiduMcpService.createTranscodingJob(
                        sourceKey, m3u8Key, resolution);
                jobId = job.getJobId();
                log.info("发起百度云转码 jobId = " + jobId + ", response = " + JSON.toJSONString(job));
                jobStatus = baiduMcpService.getTranscodingJob(jobId).getJobStatus();
                break;
            }
            case TranscodeProvider.ALIYUN_CLOUD_FUNCTION:
                jobId = IdUtil.simpleUUID();
                String callbackUrl = externalBaseUrl + "/transcode/aliyunCloudFunctionTranscodeCallback";
                cloudFunctionTranscodeService.transcode(
                        sourceKey,
                        m3u8Key.substring(0, m3u8Key.lastIndexOf("/")),
                        videoId, transcodeId, jobId, resolution, width, height,
                        VideoCodec.H264, AudioCodec.AAC, "keep", callbackUrl
                );
                break;
        }
        //保存jobId，更新jobStatus
        transcode.setJobId(jobId);
        transcode.setStatus(jobStatus);
        mongoTemplate.save(transcode);

        //异步轮询查询阿里云转码状态，并回调
        if (transcodeProvider.equals(TranscodeProvider.ALIYUN_MPS)) {
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
        String videoProvider = video.getProvider();

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
        String videoProvider = video.getProvider();

        //获取视频信息
        if (videoProvider.equals(S3Provider.ALIYUN_OSS)) {
            log.info("视频源文件上传完成，通过阿里云获取视频信息，videoId = " + videoId);
            SubmitMediaInfoJobResponseBody body = aliyunMpsService.getMediaInfo(sourceKey).getBody();
            SubmitMediaInfoJobResponseBody.SubmitMediaInfoJobResponseBodyMediaInfoJob job = body.getMediaInfoJob();
            log.info("阿里云获取视频信息返回：" + JSON.toJSONString(job));
            //给video保存媒体信息到数据库
            video.setMediaInfo(JSONObject.parseObject(JSON.toJSONString(job)));
            String jobId = job.getJobId();
            log.info("获取视频信息 jobId = " + jobId);
            SubmitMediaInfoJobResponseBody.SubmitMediaInfoJobResponseBodyMediaInfoJobProperties
                    properties = job.getProperties();
            video.setDuration((int) (Double.parseDouble(properties.getDuration()) * 1000));
            video.setHeight(Integer.parseInt(properties.getHeight()));
            video.setWidth(Integer.parseInt(properties.getWidth()));
            SubmitMediaInfoJobResponseBody.SubmitMediaInfoJobResponseBodyMediaInfoJobPropertiesStreams
                    streams = properties.getStreams();
            video.setVideoCodec(streams.getVideoStreamList().getVideoStream().get(0).getCodecName());
            video.setAudioCodec(streams.getAudioStreamList().getAudioStream().get(0).getCodecName());
            //TODO 阿里云对象存储截帧，这我云函数也可以自己做，甚至雪碧图

        } else if (videoProvider.equals(S3Provider.BAIDU_BOS)) {
            GetMediaInfoOfFileResponse mediaInfo = baiduMcpService.getMediaInfo(sourceKey);
            log.info("视频源文件上传完成，通过百度获取视频信息，videoId = " + videoId
                    + ", mediaInfo = " + JSON.toJSONString(mediaInfo));
            //截帧
            createThumbnail(user, video);
            video.setDuration(mediaInfo.getDurationInMillisecond());
            video.setMediaInfo(JSONObject.parseObject(JSON.toJSONString(mediaInfo)));
            video.setWidth(mediaInfo.getVideo().getWidthInPixel());
            video.setHeight(mediaInfo.getVideo().getHeightInPixel());
            video.setVideoCodec(mediaInfo.getVideo().getCodec());
            video.setAudioCodec(mediaInfo.getAudio().getCodec());
        }
        //更新数据库video状态
        video.setStatus(VideoStatus.TRANSCODING);
        mongoTemplate.save(video);

        //开始转码，首先一定会发起720p的转码
        transcodeSingleResolution(user, video, Resolution.R_720P);

        //如果宽高大于1280*720，再次发起1080p转码
        if (isResolutionOverThan720p(video.getWidth(), video.getHeight())) {
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
