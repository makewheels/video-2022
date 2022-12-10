package com.github.makewheels.video2022.watch;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.cover.Cover;
import com.github.makewheels.video2022.cover.CoverRepository;
import com.github.makewheels.video2022.etc.ip.IpService;
import com.github.makewheels.video2022.etc.response.ErrorCode;
import com.github.makewheels.video2022.etc.response.Result;
import com.github.makewheels.video2022.file.File;
import com.github.makewheels.video2022.file.FileRepository;
import com.github.makewheels.video2022.transcode.Transcode;
import com.github.makewheels.video2022.transcode.TranscodeRepository;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.utils.DingUtil;
import com.github.makewheels.video2022.video.VideoRedisService;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.Video;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import com.github.makewheels.video2022.watch.watchinfo.PlayUrl;
import com.github.makewheels.video2022.watch.watchinfo.WatchInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
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

    @Value("${internal-base-url}")
    private String internalBaseUrl;

    /**
     * 增加观看记录
     */
    public Result<Void> addWatchLog(
            HttpServletRequest request, String clientId, String sessionId,
            String videoId, String videoStatus) {
        //观看记录根据videoId和sessionId判断是否已存在观看记录，如果已存在则跳过
        if (watchRepository.isWatchLogExist(videoId, sessionId, videoStatus)) {
            return Result.ok();
        }
        //保存观看记录
        WatchLog watchLog = new WatchLog();
        watchLog.setCreateTime(new Date());
        String ip = request.getRemoteAddr();
        watchLog.setIp(ip);

        //查询ip归属地
        JSONObject ipResponse = ipService.getIpWithRedis(ip);
        JSONObject ipResult = ipResponse.getJSONObject("result");
        watchLog.setIpInfo(ipResult);
        String userAgent = request.getHeader("User-Agent");
        watchLog.setUserAgent(userAgent);
        watchLog.setVideoStatus(videoStatus);
        watchLog.setVideoId(videoId);
        watchLog.setClientId(clientId);
        watchLog.setSessionId(sessionId);

        mongoTemplate.save(watchLog);
        //增加video观看次数，只有视频就绪状态才增加播放量，其它状态只记录观看记录，不统计播放量
        if (videoStatus.equals(VideoStatus.READY)) {
            videoRepository.addWatchCount(videoId);
        }

        Video video = videoRepository.getById(videoId);

        //推送钉钉
        String province = ipResult.getString("province");
        String city = ipResult.getString("city");
        String district = ipResult.getString("district");
        log.info("观看记录：videoId = {}, title = {}, {} {} {} {}",
                videoId, video.getTitle(), ip, province, city, district);

        String markdownText = "# video: " + video.getTitle() + "\n\n" +
                "# viewCount: " + video.getWatchCount() + "\n\n" +
                "# ip: " + ip + "\n\n" +
                "# ipInfo: " + province + " " + city + " " + district + "\n\n" + "\n\n" +
                "# User-Agent: " + userAgent;
        DingUtil.sendMarkdown("观看记录", markdownText);

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

    private String getM3u8ContentByTranscode(Transcode transcode, String clientId, String sessionId) {
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
            String line = lines.get(i);
            if (StringUtils.startsWith(line, "#")) continue;
            File file = fileMap.get(line);
            String url = internalBaseUrl + "/file/access?"
                    + "videoId=" + transcode.getVideoId()
                    + "&clientId=" + clientId
                    + "&sessionId=" + sessionId
                    + "&resolution=" + transcode.getResolution()
                    + "&fileId=" + file.getId()
                    + "&timestamp=" + System.currentTimeMillis()
                    + "&nonce=" + IdUtil.nanoId()
                    + "&sign=" + IdUtil.simpleUUID();
            lines.set(i, url);
        }

        return StringUtils.join(lines, "\n");
    }

    /**
     * 根据转码对象获取m3u8内容，返回String
     */
    public String getM3u8Content(String videoId, String clientId, String sessionId,
                                 String transcodeId, String resolution) {
        Transcode transcode = transcodeRepository.getById(transcodeId);
        return getM3u8ContentByTranscode(transcode, clientId, sessionId);
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
    public String getMultivariantPlaylist(String videoId, String clientId, String sessionId) {
        Video video = videoRepository.getById(videoId);
        List<Transcode> transcodeList = transcodeRepository.getByIds(video.getTranscodeIds());
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("#EXTM3U\n");
        for (Transcode transcode : transcodeList) {
            stringBuilder.append("#EXT-X-STREAM-INF:BANDWIDTH=").append(transcode.getMaxBitrate())
                    .append(",AVERAGE-BANDWIDTH=").append(transcode.getAverageBitrate())
                    .append(getM3u8ContentByTranscode(transcode, clientId, sessionId))
                    .append("\n");
        }
        return stringBuilder.toString();
    }
}
