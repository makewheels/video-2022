package com.github.makewheels.video2022.video;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
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
import com.github.makewheels.video2022.watch.WatchRepository;
import com.github.makewheels.video2022.watch.watchinfo.PlayUrl;
import com.github.makewheels.video2022.watch.watchinfo.WatchInfo;
import com.github.makewheels.video2022.watch.WatchLog;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
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

    @Value("${internal-base-url}")
    private String internalBaseUrl;
    @Value("${short-url-service}")
    private String shortUrlService;

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
            provider = Provider.BAIDU;
        } else if (type.equals(VideoType.YOUTUBE)) {
            provider = Provider.ALIYUN;
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
        //如果是用户上传，就没有这个步骤
        if (type.equals(VideoType.YOUTUBE)) {
            new Thread(() -> {
                //提交任务给海外服务器
                youtubeService.submitMission(user, video,file);
                //获取视频信息
                JSONObject jsonObject = youtubeService.getVideoInfo(video);
                //更新数据库保存的title和description
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
     * 转码任务，盲分辨率，子线程异步执行
     *
     * @param user
     * @param video
     * @param resolution
     */
    private void transcode(User user, Video video, String resolution) {
        String userId = user.getId();
        String videoId = video.getId();
        String sourceKey = video.getOriginalFileKey();

        Transcode transcode = new Transcode();
        transcode.setUserId(userId);
        transcode.setVideoId(videoId);
        transcode.setCreateTime(new Date());
        transcode.setStatus(BaiduTranscodeStatus.CREATED);
        transcode.setResolution(resolution);
        transcode.setSourceKey(sourceKey);
        String m3u8Key = "videos/" + userId + "/" + videoId + "/transcode/"
                + resolution + "/" + videoId + ".m3u8";
        transcode.setM3u8Key(m3u8Key);
        transcode.setM3u8AccessUrl("fileService.getAliyunOssAccessBaseUrl()" + m3u8Key);
        transcode.setM3u8CdnUrl("fileService.getAliyunOssCdnBaseUrl()" + m3u8Key);
        mongoTemplate.save(transcode);

        //发起转码
        CreateTranscodingJobResponse transcodingJob = transcodeService.createTranscodingJob(
                sourceKey, m3u8Key, resolution);
        String jobId = transcodingJob.getJobId();
        log.info("发起 " + resolution + " 转码：videoId = " + videoId);
        log.info(JSON.toJSONString(transcodingJob));
        transcode.setJobId(jobId);
        mongoTemplate.save(transcode);

        //再次更新数据库状态
        transcode.setStatus(transcodeService.getTranscodingJob(jobId).getJobStatus());
        mongoTemplate.save(transcode);
    }

    /**
     * 原始文件上传完成，开始转码
     *
     * @param user
     * @param videoId
     * @return
     */
    public Result<Void> originalFileUploadFinish(User user, String videoId) {
        String userId = user.getId();
        //查数据库，找到video
        Video video = mongoTemplate.findById(videoId, Video.class);
        if (video == null) return Result.error(ErrorCode.FAIL);

        File file = mongoTemplate.findById(video.getOriginalFileId(), File.class);
        if (file == null) return Result.error(ErrorCode.FAIL);

        if (!file.getStatus().equals(FileStatus.READY))
            return Result.error(ErrorCode.FAIL);

        String sourceKey = video.getOriginalFileKey();
        video.setStatus(VideoStatus.TRANSCODING);
        mongoTemplate.save(video);

        String provider = video.getProvider();

        //创建子线程执行转码任务，先给前端返回结果
        new Thread(() -> {
            //获取视频信息
            GetMediaInfoOfFileResponse mediaInfo = transcodeService.getMediaInfo(sourceKey);
            log.info("源文件上传完成，获取mediaInfo：videoId = " + videoId);
            log.info(JSON.toJSONString(mediaInfo));

            video.setDuration(mediaInfo.getDurationInMillisecond());
            video.setMediaInfo(JSONObject.parseObject(JSON.toJSONString(mediaInfo)));
            mongoTemplate.save(video);

            //发起截帧任务
            String targetKeyPrefix = "videos/" + userId + "/" + videoId + "/cover/" + videoId;
            CreateThumbnailJobResponse thumbnailJob
                    = thumbnailService.createThumbnailJob(sourceKey, targetKeyPrefix);
            log.info("发起截帧任务：video = " + videoId);
            log.info(JSON.toJSONString(thumbnailJob));
            String thumbnailJobId = thumbnailJob.getJobId();
            Thumbnail thumbnail = new Thumbnail();
            String key = targetKeyPrefix + ".jpg";
            thumbnail.setCreateTime(new Date());
            thumbnail.setUserId(userId);
            thumbnail.setVideoId(videoId);
            thumbnail.setJobId(thumbnailJobId);
            thumbnail.setStatus(BaiduTranscodeStatus.CREATED);
            thumbnail.setSourceKey(sourceKey);
            thumbnail.setTargetKeyPrefix(targetKeyPrefix);
            thumbnail.setAccessUrl("fileService.getAliyunOssAccessBaseUrl()" + key);
            thumbnail.setCdnUrl("fileService.getAliyunOssCdnBaseUrl() "+ key);
            thumbnail.setExtension("jpg");
            thumbnail.setKey(key);
            mongoTemplate.save(thumbnail);
            //再次查询，更新状态
            thumbnail.setStatus(thumbnailService.getThumbnailJob(thumbnailJobId).getJobStatus());
            mongoTemplate.save(thumbnail);

            //更新video的冗余字段coverUrl
            video.setCoverUrl(thumbnail.getCdnUrl());
            mongoTemplate.save(video);

            Integer width = mediaInfo.getVideo().getWidthInPixel();
            Integer height = mediaInfo.getVideo().getHeightInPixel();

            //开始转码
            //首先一定会发起720p的转码
            transcode(user, video, Resolution.R_720P);

            //如果单边大于1280像素，再次发起1080p转码
            if (Math.max(width, height) > 1280) {
                transcode(user, video, Resolution.R_1080P);
            }
        }).start();

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
        videoInfo.setCreateTimeString(DateUtil.formatDateTime(videoInfo.getCreateTime()));
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

    /**
     * 在cdn预热完成时
     *
     * @param body
     * @return
     */
    public Result<Void> onCdnPrefetchFinish(JSONObject body) {
        log.info("收到软路由预热完成回调：");
        log.info(body.toJSONString());
        return Result.ok();
    }
}
