package com.github.makewheels.video2022.video;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.cover.CoverLauncher;
import com.github.makewheels.video2022.system.environment.EnvironmentService;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.utils.IdService;
import com.github.makewheels.video2022.utils.PathUtil;
import com.github.makewheels.video2022.utils.ShortUrlService;
import com.github.makewheels.video2022.video.bean.dto.CreateVideoDTO;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.bean.entity.Watch;
import com.github.makewheels.video2022.video.bean.entity.YouTube;
import com.github.makewheels.video2022.video.constants.VideoType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

@Service
@Slf4j
public class VideoCreateService {
    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private YoutubeService youtubeService;
    @Resource
    private IdService idService;
    @Resource
    private FileService fileService;
    @Resource
    private CoverLauncher coverLauncher;
    @Resource
    private ShortUrlService shortUrlService;

    @Resource
    private EnvironmentService environmentService;

    /**
     * 创建视频对象
     */
    private Video createVideo(CreateVideoDTO createVideoDTO) {
        User user = UserHolder.get();
        createVideoDTO.setUser(user);
        String userId = user.getId();

        //创建 video
        Video video = new Video();
        createVideoDTO.setVideo(video);
        video.setUploaderId(userId);

        String videoType = createVideoDTO.getVideoType();
        video.setType(videoType);

        //YouTube
        if (videoType.equals(VideoType.YOUTUBE)) {
            String youtubeUrl = createVideoDTO.getYoutubeUrl();
            YouTube youTube = new YouTube();
            youTube.setUrl(youtubeUrl);
            youTube.setVideoId(youtubeService.getYoutubeVideoIdByUrl(youtubeUrl));
            video.setYouTube(youTube);
        }

        //创建 video file
        File videoFile = fileService.createVideoFile(createVideoDTO);
        createVideoDTO.setRawFile(videoFile);

        String fileId = videoFile.getId();
        video.setRawFileId(fileId);
        String watchId = idService.nextShortId();
        Watch watch = video.getWatch();
        watch.setWatchId(watchId);
        String watchUrl = environmentService.getInternalBaseUrl() + "/watch?v=" + watchId;
        watch.setWatchUrl(watchUrl);

        //本地开发环境shortUrl就是watchUrl
        watch.setShortUrl(watchUrl);

        if (environmentService.isProductionEnv()) {
            String shortUrl = shortUrlService.getShortUrl(watchUrl);
            watch.setShortUrl(shortUrl);
        }

        //设置过期时间
        long expireTimeInMillis = Duration.ofDays(30).toMillis();
        Date expireTime = new Date(System.currentTimeMillis() + expireTimeInMillis);
        video.getStorageStatus().setExpireTime(expireTime);
        mongoTemplate.save(video);

        return video;
    }

    /**
     * 创建新视频
     */
    public void create(CreateVideoDTO createVideoDTO) {
        // 新建video对象
        Video video = createVideo(createVideoDTO);
        String videoId = video.getId();
        File rawFile = createVideoDTO.getRawFile();
        createVideoDTO.setRawFile(rawFile);

        //更新file
        rawFile.setVideoId(videoId);
        rawFile.setKey(PathUtil.getRawFilePrefix(UserHolder.getUserId(), videoId)
                + "/" + rawFile.getId() + "." + rawFile.getExtension());
        mongoTemplate.save(rawFile);
        log.info("新建文件：" + JSON.toJSONString(rawFile));

        log.info("新建视频：" + JSON.toJSONString(video));

        //如果是搬运YouTube视频，多一个步骤，通知海外服务器
        if (video.getType().equals(VideoType.YOUTUBE)) {
            new Thread(() -> handleCreateYoutube(video, UserHolder.get(), rawFile)).start();
        }
    }

    /**
     * 创建视频：处理youtube
     */
    private void handleCreateYoutube(Video video, User user, File videoFile) {
        String extension = youtubeService.getFileExtension(video.getYouTube().getVideoId());
        if (!videoFile.getExtension().equals(extension)) {
            //更新file
            videoFile.setExtension(extension);
            String newKey = videoFile.getKey();
            videoFile.setKey(newKey.substring(0, newKey.lastIndexOf(".")) + "." + extension);
            mongoTemplate.save(videoFile);
        }

        //提交搬运视频任务给海外服务器
        youtubeService.transferVideo(user, video, videoFile);

        //获取视频信息，保存title和description到数据库
        JSONObject youtubeVideoInfo = youtubeService.getVideoInfo(video.getYouTube().getVideoId());
        video.getYouTube().setVideoInfo(youtubeVideoInfo);

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
        Date publishTime = Date.from(
                ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).toInstant());
        video.getYouTube().setPublishTime(publishTime);

        JSONObject snippet = youtubeVideoInfo.getJSONObject("snippet");
        video.setTitle(snippet.getString("title"));
        video.setDescription(snippet.getString("description"));
        mongoTemplate.save(video);
    }
}
