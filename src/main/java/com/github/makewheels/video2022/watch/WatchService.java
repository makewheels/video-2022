package com.github.makewheels.video2022.watch;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.cover.CoverService;
import com.github.makewheels.video2022.etc.ding.NotificationService;
import com.github.makewheels.video2022.file.TsFileRepository;
import com.github.makewheels.video2022.file.bean.TsFile;
import com.github.makewheels.video2022.system.context.Context;
import com.github.makewheels.video2022.system.context.RequestUtil;
import com.github.makewheels.video2022.system.environment.EnvironmentService;
import com.github.makewheels.video2022.system.response.ErrorCode;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.transcode.TranscodeRepository;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.utils.IpService;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.bean.entity.Watch;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import com.github.makewheels.video2022.watch.watchinfo.WatchInfoVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
    private TsFileRepository tsFileRepository;

    @Resource
    private CoverService coverService;
    @Resource
    private NotificationService notificationService;
    @Resource
    private EnvironmentService environmentService;

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
        JSONObject ipResult = ipService.getIpWithRedis(ip);
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

        Video video = videoRepository.getById(videoId);
        //增加video观看次数
        if (videoStatus.equals(VideoStatus.READY)) {
            videoRepository.addWatchCount(videoId);
            //刷新Redis缓存
            Watch watch = video.getWatch();
            watch.setWatchCount(watch.getWatchCount() + 1);
            mongoTemplate.save(video);
        }

        //保存观看记录到数据库
        saveWatchLog(context, video);

        String ip = RequestUtil.getIp();
        JSONObject ipResult = ipService.getIpWithRedis(ip);
        String province = ipResult.getString("province");
        String city = ipResult.getString("city");
        String district = ipResult.getString("district");
        log.info("观看记录：videoId = {}, title = {}, {} {} {} {}",
                videoId, video.getTitle(), ip, province, city, district);

        //推送钉钉
        if (environmentService.isProductionEnv()) {
            notificationService.sendWatchLogMessage(video, ipResult);
        }

        return Result.ok();
    }

    /**
     * 返回的这个url，能获取m3u8内容
     */
    private String getM3u8Url(String videoId, String clientId, String sessionId, String transcodeId,
                              String resolution) {
        return environmentService.getInternalBaseUrl() + "/watchController/getM3u8Content.m3u8?"
                + "resolution=" + resolution
                + "&videoId=" + videoId
                + "&clientId=" + clientId
                + "&sessionId=" + sessionId
                + "&transcodeId=" + transcodeId;
    }

    /**
     * 获取播放信息
     */
    public Result<WatchInfoVO> getWatchInfo(Context context, String watchId) {
        Video video = videoRepository.getByWatchId(watchId);
        if (video == null) {
            log.warn("查不到这个video, watchId = " + watchId);
            return Result.error(ErrorCode.VIDEO_NOT_EXIST);
        }
        String videoId = video.getId();
        WatchInfoVO watchInfoVO = new WatchInfoVO();
        watchInfoVO.setVideoId(videoId);
        //通过videoId查找封面
        String coverUrl = coverService.getSignedCoverUrl(video.getCoverId());
        watchInfoVO.setCoverUrl(coverUrl);

        watchInfoVO.setVideoStatus(video.getStatus());

        //自适应m3u8地址
        watchInfoVO.setMultivariantPlaylistUrl(environmentService.getInternalBaseUrl()
                + "/watchController/getMultivariantPlaylist.m3u8?videoId=" + videoId
                + "&clientId=" + context.getClientId() + "&sessionId=" + context.getSessionId());
        return Result.ok(watchInfoVO);
    }

    /**
     * 根据转码对象获取m3u8内容，返回String
     */
    public String getM3u8Content(Context context, String transcodeId, String resolution) {
        Transcode transcode = transcodeRepository.getById(transcodeId);
        //找到transcode对应的tsFiles
        List<TsFile> tsFiles = tsFileRepository.getByIds(transcode.getTsFileIds());
        Map<String, TsFile> fileMap = tsFiles.stream().collect(
                Collectors.toMap(TsFile::getFilename, Function.identity()));

        String m3u8Content = transcode.getM3u8Content();

        //拆解m3u8Content
        List<String> lines = Arrays.asList(m3u8Content.split("\n"));
        for (int i = 0; i < lines.size(); i++) {
            String filename = lines.get(i);
            if (StringUtils.startsWith(filename, "#")) continue;
            TsFile tsFile = fileMap.get(filename);
            String url = environmentService.getInternalBaseUrl() + "/file/access?"
                    + "resolution=" + transcode.getResolution()
                    + "&tsIndex=" + fileMap.get(filename).getTsIndex()
                    + "&videoId=" + context.getVideoId()
                    + "&clientId=" + context.getClientId()
                    + "&sessionId=" + context.getSessionId()
                    + "&fileId=" + tsFile.getId()
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

        Video video = videoRepository.getById(videoId);
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
                    .append(m3u8Url)
                    .append("\n");
        }
        return stringBuilder.toString();
    }
}
