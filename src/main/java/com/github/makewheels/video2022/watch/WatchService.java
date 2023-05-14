package com.github.makewheels.video2022.watch;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.cover.Cover;
import com.github.makewheels.video2022.cover.CoverRepository;
import com.github.makewheels.video2022.etc.context.Context;
import com.github.makewheels.video2022.etc.context.RequestUtil;
import com.github.makewheels.video2022.etc.response.ErrorCode;
import com.github.makewheels.video2022.etc.response.Result;
import com.github.makewheels.video2022.file.File;
import com.github.makewheels.video2022.file.FileRepository;
import com.github.makewheels.video2022.redis.CacheService;
import com.github.makewheels.video2022.transcode.TranscodeRepository;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.utils.DingService;
import com.github.makewheels.video2022.utils.IpService;
import com.github.makewheels.video2022.video.VideoRedisService;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.video.Video;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import com.github.makewheels.video2022.watch.watchinfo.WatchInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class WatchService {
    @Resource
    private IpService ipService;
    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private WatchRepository watchRepository;
    @Resource
    private VideoRepository videoRepository;
    @Resource
    private TranscodeRepository transcodeRepository;
    @Resource
    private FileRepository fileRepository;
    @Resource
    private CoverRepository coverRepository;

    @Resource
    private VideoRedisService videoRedisService;
    @Resource
    private CacheService cacheService;
    @Resource
    private DingService dingService;

    @Value("${internal-base-url}")
    private String internalBaseUrl;

    /**
     * 保存观看记录到数据库
     */
    private void saveWatchLog(Context context, Video video) {
        //保存观看记录
        WatchLog watchLog = new WatchLog();
        watchLog.setCreateTime(new Date());
        String ip = RequestUtil.getIp();
        watchLog.setIp(ip);

        //查询ip归属地
        JSONObject ipResult = ipService.getIpResultWithRedis(ip);
        watchLog.setIpInfo(ipResult);
        String userAgent = RequestUtil.getUserAgent();
        watchLog.setUserAgent(userAgent);
        watchLog.setVideoStatus(video.getStatus());
        watchLog.setVideoId(video.getId());
        watchLog.setClientId(context.getClientId());
        watchLog.setSessionId(context.getSessionId());

        mongoTemplate.save(watchLog);
    }

    /**
     * 增加观看记录
     */
    public Result<Void> addWatchLog(Context context, String videoStatus) {
        String videoId = context.getVideoId();
        String sessionId = context.getSessionId();

        //观看记录根据videoId和sessionId判断是否已存在观看记录，如果已存在则跳过
        if (watchRepository.isWatchLogExist(videoId, sessionId, videoStatus)) {
            return Result.ok();
        }

        Video video = cacheService.getVideo(videoId);
        //增加video观看次数
        if (videoStatus.equals(VideoStatus.READY)) {
            videoRepository.addWatchCount(videoId);
            //刷新Redis缓存
            video.setWatchCount(video.getWatchCount() + 1);
            cacheService.updateVideo(video);
        }

        //保存观看记录到数据库
        saveWatchLog(context, video);

        //推送钉钉
        String ip = RequestUtil.getIp();
        JSONObject ipResult = ipService.getIpResultWithRedis(ip);
        String province = ipResult.getString("province");
        String city = ipResult.getString("city");
        String district = ipResult.getString("district");
        log.info("观看记录：videoId = {}, title = {}, {} {} {} {}",
                videoId, video.getTitle(), ip, province, city, district);

        String markdownText = "# video: " + video.getTitle() + "\n\n" +
                "# viewCount: " + video.getWatchCount() + "\n\n" +
                "# ip: " + ip + "\n\n" +
                "# ipInfo: " + province + " " + city + " " + district + "\n\n" + "\n\n" +
                "# User-Agent: " + RequestUtil.getUserAgent();
        dingService.sendMarkdown("观看记录", markdownText);

        return Result.ok();
    }

    /**
     * 返回的这个url，能获取m3u8内容
     */
    private String getM3u8Url(String videoId, String clientId, String sessionId, String transcodeId,
                              String resolution) {
        return internalBaseUrl + "/watchController/getM3u8Content.m3u8?"
                + "resolution=" + resolution
                + "&videoId=" + videoId
                + "&clientId=" + clientId
                + "&sessionId=" + sessionId
                + "&transcodeId=" + transcodeId;
    }

    /**
     * 获取播放信息
     */
    public Result<WatchInfo> getWatchInfo(Context context, String watchId) {
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

        watchInfo.setVideoStatus(video.getStatus());

        //自适应m3u8地址
        watchInfo.setMultivariantPlaylistUrl(internalBaseUrl
                + "/watchController/getMultivariantPlaylist.m3u8?videoId=" + videoId
                + "&clientId=" + context.getClientId() + "&sessionId=" + context.getSessionId());
        //缓存redis，先判断视频状态：只有READY才放入缓存
        if (video.isReady()) {
            videoRedisService.setWatchInfo(watchId, watchInfo);
        }
        return Result.ok(watchInfo);
    }

    /**
     * 根据转码对象获取m3u8内容，返回String
     */
    public String getM3u8Content(Context context, String transcodeId, String resolution) {
        Transcode transcode = transcodeRepository.getById(transcodeId);
        //找到transcode对应的tsFiles
        List<File> files = fileRepository.getByIds(transcode.getTsFileIds());
        Map<String, File> fileMap = files.stream().collect(
                Collectors.toMap(File::getFilename, Function.identity()));

        String m3u8Content = transcode.getM3u8Content();
        //TODO 这里需要缓存，key是transcodeId，value是Transcode
        //TODO 还需要一个 files缓存

        //拆解m3u8Content
        List<String> lines = Arrays.asList(m3u8Content.split("\n"));
        for (int i = 0; i < lines.size(); i++) {
            String filename = lines.get(i);
            if (StringUtils.startsWith(filename, "#")) continue;
            File file = fileMap.get(filename);
            String url = internalBaseUrl + "/file/access?"
                    + "resolution=" + transcode.getResolution()
                    + "&tsIndex=" + fileMap.get(filename).getTsIndex()
                    + "&videoId=" + context.getVideoId()
                    + "&clientId=" + context.getClientId()
                    + "&sessionId=" + context.getSessionId()
                    + "&fileId=" + file.getId()
                    + "&timestamp=" + System.currentTimeMillis()
                    + "&nonce=" + IdUtil.nanoId()
                    + "&sign=" + IdUtil.simpleUUID();
            lines.set(i, url);
        }
        return StringUtils.join(lines, "\n");
    }

    /**
     * 获取自适应m3u8列表
     * https://developer.apple.com/documentation/http_live_streaming
     * /example_playlists_for_http_live_streaming/creating_a_multivariant_playlist
     * <p>
     * 例子：
     * #EXTM3U
     * <p>
     * #EXT-X-STREAM-INF:BANDWIDTH=150000,RESOLUTION=416x234,CODECS="avc1.42e00a,mp4a.40.2"
     * http://example.com/low/index.m3u8
     * <p>
     * #EXT-X-STREAM-INF:BANDWIDTH=240000,RESOLUTION=416x234,CODECS="avc1.42e00a,mp4a.40.2"
     * http://example.com/lo_mid/index.m3u8
     * <p>
     * #EXT-X-STREAM-INF:BANDWIDTH=440000,RESOLUTION=416x234,CODECS="avc1.42e00a,mp4a.40.2"
     * http://example.com/hi_mid/index.m3u8
     */
    public String getMultivariantPlaylist(Context context) {
        String videoId = context.getVideoId();
        String clientId = context.getClientId();
        String sessionId = context.getSessionId();

        Video video = cacheService.getVideo(videoId);
        List<Transcode> transcodeList = transcodeRepository.getByIds(video.getTranscodeIds());
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("#EXTM3U\n");
        for (Transcode transcode : transcodeList) {
            String m3u8Url = getM3u8Url(videoId, clientId, sessionId, transcode.getId(),
                    transcode.getResolution());

            stringBuilder.append("#EXT-X-STREAM-INF:BANDWIDTH=")
                    .append(transcode.getMaxBitrate())
                    .append(",AVERAGE-BANDWIDTH=").append(transcode.getAverageBitrate())
                    .append("\n")
                    .append(m3u8Url).append("\n");
        }
        return stringBuilder.toString();
    }
}
