package com.github.makewheels.video2022.video;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.cover.CoverLauncher;
import com.github.makewheels.video2022.cover.CoverService;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.file.constants.FileStatus;
import com.github.makewheels.video2022.redis.CacheService;
import com.github.makewheels.video2022.springboot.exception.VideoException;
import com.github.makewheels.video2022.system.response.ErrorCode;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.transcode.TranscodeLauncher;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.dto.CreateVideoDTO;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.bean.entity.YouTube;
import com.github.makewheels.video2022.video.bean.vo.VideoVO;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import com.github.makewheels.video2022.video.constants.VideoType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class VideoService {
    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private TranscodeLauncher transcodeLauncher;
    @Resource
    private CoverLauncher coverLauncher;
    @Resource
    private CoverService coverService;

    @Resource
    private FileService fileService;
    @Resource
    private VideoCreateService videoCreateService;

    @Resource
    private VideoRepository videoRepository;
    @Resource
    private CacheService cacheService;

    /**
     * 创建新视频
     */
    public JSONObject create(CreateVideoDTO createVideoDTO) {
        // 调用createService
        videoCreateService.create(createVideoDTO);

        // 返回给前端
        Video video = createVideoDTO.getVideo();
        File videoFile = createVideoDTO.getVideoFile();

        JSONObject response = new JSONObject();
        response.put("fileId", videoFile.getId());
        response.put("videoId", video.getId());
        response.put("watchId", video.getWatch().getWatchId());
        response.put("watchUrl", video.getWatch().getWatchUrl());
        response.put("shortUrl", video.getWatch().getShortUrl());
        return response;
    }

    /**
     * 原始文件上传完成，开始转码
     */
    public void originalFileUploadFinish(String videoId) {
        User user = UserHolder.get();
        //查数据库，找到video
        Video video = cacheService.getVideo(videoId);

        //校验
        if (video == null) throw new VideoException(ErrorCode.VIDEO_NOT_EXIST);

        File file = mongoTemplate.findById(video.getOriginalFileId(), File.class);
        if (file == null) throw new VideoException(ErrorCode.FILE_NOT_EXIST);

        if (!file.getFileStatus().equals(FileStatus.READY)) throw new VideoException(ErrorCode.FILE_NOT_READY);

        //更新视频为正在转码状态
        video.setStatus(VideoStatus.TRANSCODING);
        cacheService.updateVideo(video);

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
    public Video updateVideo(Video newVideo) {
        User user = UserHolder.get();
        String userId = user.getId();
        String videoId = newVideo.getId();
        Video oldVideo = cacheService.getVideo(videoId);
        //判断视频是否存在
        if (oldVideo == null) {
            throw new VideoException(ErrorCode.VIDEO_NOT_EXIST);
        }
        //判断视频是否属于当前用户
        if (!StringUtils.equals(userId, oldVideo.getUploaderId())) {
            throw new VideoException(ErrorCode.VIDEO_AND_UPLOADER_NOT_MATCH);
        }

        oldVideo.setTitle(newVideo.getTitle());
        oldVideo.setDescription(newVideo.getDescription());
        cacheService.updateVideo(oldVideo);

        log.info("更新视频信息：videoId = {}, title = {}, description = {}",
                videoId, oldVideo.getTitle(), oldVideo.getDescription());
        return oldVideo;
    }

    /**
     * 获取视频详情
     */
    public VideoVO getVideoDetail(String videoId) {
        Video video = videoRepository.getById(videoId);
        VideoVO videoVO = new VideoVO();
        videoVO.setId(video.getId());
        videoVO.setUserId(video.getUploaderId());
        videoVO.setType(video.getType());
        videoVO.setStatus(video.getStatus());
        videoVO.setTitle(video.getTitle());
        videoVO.setDescription(video.getDescription());
        videoVO.setCoverUrl(coverService.getSignedCoverUrl(video.getCoverId()));
        videoVO.setDuration(video.getMediaInfo().getDuration());
        videoVO.setWatchCount(video.getWatch().getWatchCount());
        videoVO.setWatchId(video.getWatch().getWatchId());
        videoVO.setWatchUrl(video.getWatch().getWatchUrl());
        videoVO.setShortUrl(video.getWatch().getShortUrl());

        videoVO.setCreateTime(video.getCreateTime());
        videoVO.setCreateTimeString(DateUtil.formatDateTime(video.getCreateTime()));
        if (VideoType.YOUTUBE.equals(video.getType())) {
            videoVO.setYoutubePublishTime(video.getYouTube().getPublishTime());
            videoVO.setYoutubePublishTimeString(DateUtil.formatDateTime(video.getYouTube().getPublishTime()));
        }
        return videoVO;
    }

    /**
     * 分页获取指定userId视频列表
     */
    private List<VideoVO> getVideoList(String userId, int skip, int limit) {
        List<Video> videos = videoRepository.getVideosByUserId(userId, skip, limit);
        // 获取封面url
        List<String> coverIdList = videos.stream().map(Video::getCoverId).collect(Collectors.toList());
        Map<String, String> coverId2UrlMap = coverService.getSignedCoverUrl(coverIdList);

        List<VideoVO> videoVOList = new ArrayList<>(videos.size());
        for (Video video : videos) {
            VideoVO videoVO = new VideoVO();
            BeanUtils.copyProperties(video, videoVO);
            videoVO.setCoverUrl(coverId2UrlMap.get(video.getCoverId()));
            videoVO.setCreateTimeString(DateUtil.formatDateTime(video.getCreateTime()));
            if (video.isYoutube() && video.getYouTube() != null) {
                YouTube youTube = video.getYouTube();
                if (youTube.getPublishTime() != null) {
                    videoVO.setYoutubePublishTimeString(DateUtil.formatDateTime(youTube.getPublishTime()));
                }
            }
            videoVOList.add(videoVO);
        }
        return videoVOList;
    }

    /**
     * 分页获取我的视频列表
     */
    public Result<List<VideoVO>> getMyVideoList(int skip, int limit) {
        List<VideoVO> videoVOList = getVideoList(UserHolder.getUserId(), skip, limit);
        return Result.ok(videoVOList);
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
        Video video = cacheService.getVideo(videoId);
        String originalFileKey = fileService.getKey(video.getOriginalFileId());
        return fileService.generatePresignedUrl(originalFileKey, Duration.ofHours(2));
    }
}
