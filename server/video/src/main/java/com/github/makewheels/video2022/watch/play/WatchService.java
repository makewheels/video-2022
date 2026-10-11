package com.github.makewheels.video2022.watch.play;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.cover.CoverService;
import com.github.makewheels.video2022.file.FileAccessSignatureService;
import com.github.makewheels.video2022.system.context.Context;
import com.github.makewheels.video2022.system.context.RequestUtil;
import com.github.makewheels.video2022.system.environment.EnvironmentService;
import com.github.makewheels.video2022.system.response.ErrorCode;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.file.TsFileRepository;
import com.github.makewheels.video2022.file.bean.TsFile;
import com.github.makewheels.video2022.transcode.M3u8Util;
import com.github.makewheels.video2022.transcode.TranscodeRepository;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.utils.IpService;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.bean.entity.Watch;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import com.github.makewheels.video2022.video.constants.Visibility;
import com.github.makewheels.video2022.watch.progress.Progress;
import com.github.makewheels.video2022.watch.progress.ProgressService;
import com.github.makewheels.video2022.watch.watchinfo.WatchInfoVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
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
    private EnvironmentService environmentService;
    @Resource
    private ProgressService progressService;
    @Resource
    private FileAccessSignatureService fileAccessSignatureService;

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
        JSONObject ipResult = ipService.getIpInfo(ip);
        watchLog.setIpInfo(ipResult);
        String userAgent = RequestUtil.getUserAgent();
        watchLog.setUserAgent(userAgent);
        watchLog.setVideoStatus(video.getStatus());
        watchLog.setVideoId(video.getId());
        watchLog.setClientId(context.getClientId());
        watchLog.setSessionId(context.getSessionId());
        watchLog.setViewerId(UserHolder.getUserId());

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
        if (video == null) {
            log.warn("addWatchLog: 视频不存在, videoId = {}", videoId);
            return Result.error(ErrorCode.VIDEO_NOT_EXIST);
        }
        //增加video观看次数
        if (videoStatus.equals(VideoStatus.READY)) {
            videoRepository.addWatchCount(videoId);
        }

        //保存观看记录到数据库
        saveWatchLog(context, video);

        String ip = RequestUtil.getIp();
        JSONObject ipResult = ipService.getIpInfo(ip);
        String province = ipResult.getString("province");
        String city = ipResult.getString("city");
        String district = ipResult.getString("district");
        log.info("观看记录：videoId = {}, title = {}, {} {} {} {}",
                videoId, video.getTitle(), ip, province, city, district);

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
            log.warn("getWatchInfo: 视频不存在, watchId = {}", watchId);
            return Result.error(ErrorCode.VIDEO_NOT_EXIST);
        }

        // 可见性检查：PRIVATE 视频仅所有者可观看
        if (Visibility.PRIVATE.equals(video.getVisibility())) {
            String currentUserId = UserHolder.getUserId();
            if (currentUserId == null || !currentUserId.equals(video.getUploaderId())) {
                return Result.error("该视频为私密视频");
            }
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

        //视频播放进度
        Progress progress = progressService.getProgress(
                videoId, UserHolder.getUserId(), context.getClientId());
        watchInfoVO.setProgressInMillis(progress == null ? 0 : progress.getProgressInMillis());

        return Result.ok(watchInfoVO);
    }

    /**
     * 根据转码对象获取m3u8内容，返回String。
     * 兼容 TS 与 fMP4：fMP4 的 #EXT-X-MAP init segment 同样走签名访问。
     */
    public String getM3u8Content(Context context, String transcodeId, String resolution) {
        Transcode transcode = transcodeRepository.getById(transcodeId);
        //找到transcode对应的tsFiles
        List<TsFile> tsFiles = tsFileRepository.getByIds(transcode.getTsFileIds());
        Map<String, TsFile> fileMap = new java.util.HashMap<>();
        for (TsFile tsFile : tsFiles) {
            fileMap.put(tsFile.getFilename(), tsFile);
        }

        String m3u8Content = transcode.getM3u8Content();

        //拆解m3u8Content
        List<String> lines = new java.util.ArrayList<>(Arrays.asList(m3u8Content.split("\n")));
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (StringUtils.startsWith(line, "#")) {
                // fMP4 init segment：替换 EXT-X-MAP 的 URI
                if (StringUtils.startsWith(line, "#EXT-X-MAP:")) {
                    String initUri = M3u8Util.getInitSegmentUri(m3u8Content);
                    TsFile initFile = initUri == null ? null : fileMap.get(initUri);
                    if (initFile != null) {
                        lines.set(i, replaceLineWithSignedUrl(line, initUri, context, transcode, initFile));
                    }
                }
                continue;
            }
            TsFile tsFile = fileMap.get(line);
            if (tsFile == null) continue;
            lines.set(i, buildSignedAccessUrl(context, transcode, tsFile));
        }
        return StringUtils.join(lines, "\n");
    }

    /**
     * 把 EXT-X-MAP 行中的 URI 值替换为签名 URL，保留其余属性
     */
    private String replaceLineWithSignedUrl(String line, String initUri, Context context,
                                            Transcode transcode, TsFile initFile) {
        String signed = buildSignedAccessUrl(context, transcode, initFile);
        String uriToken = "URI=\"" + initUri + "\"";
        return line.replace(uriToken, "URI=\"" + signed + "\"");
    }

    /**
     * 构建带签名的分片访问 URL（TS 与 fMP4 init/分片共用）
     */
    private String buildSignedAccessUrl(Context context, Transcode transcode, TsFile tsFile) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = IdUtil.nanoId();
        String signature = fileAccessSignatureService.generateSignature(
                context.getVideoId(),
                context.getClientId(),
                context.getSessionId(),
                transcode.getResolution(),
                tsFile.getId(),
                timestamp,
                nonce
        );
        return environmentService.getInternalBaseUrl() + "/file/access?"
                + "resolution=" + transcode.getResolution()
                + "&tsIndex=" + tsFile.getTsIndex()
                + "&fileType=" + tsFile.getFileType()
                + "&videoId=" + context.getVideoId()
                + "&clientId=" + context.getClientId()
                + "&sessionId=" + context.getSessionId()
                + "&fileId=" + tsFile.getId()
                + "&timestamp=" + timestamp
                + "&nonce=" + nonce
                + "&sign=" + signature;
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
        if (video == null) {
            log.warn("getMultivariantPlaylist: 视频不存在, videoId = {}", videoId);
            return "";
        }
        List<Transcode> transcodeList = transcodeRepository.getByIds(video.getTranscodeIds());
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("#EXTM3U\n");
        for (Transcode transcode : transcodeList) {
            // 主播放列表只发布登记成功且产物完整的档位，失败/进行中档位不出现
            if (!transcode.isSuccessStatus()
                    || transcode.getTsFileIds() == null
                    || transcode.getTsFileIds().isEmpty()) {
                continue;
            }
            String m3u8Url = getM3u8Url(videoId, clientId, sessionId, transcode.getId(),
                    transcode.getResolution());

            stringBuilder.append("#EXT-X-STREAM-INF:")
                    .append("BANDWIDTH=").append(transcode.getMaxBitrate())
                    .append(",AVERAGE-BANDWIDTH=").append(transcode.getAverageBitrate());
            // 真实输出属性：有数据才写，不造假值
            Integer w = transcode.getActualWidth() != null
                    ? transcode.getActualWidth() : transcode.getWidth();
            Integer h = transcode.getActualHeight() != null
                    ? transcode.getActualHeight() : transcode.getHeight();
            if (w != null && h != null) {
                stringBuilder.append(",RESOLUTION=").append(w).append("x").append(h);
            }
            if (StringUtils.isNotBlank(transcode.getActualCodecs())) {
                stringBuilder.append(",CODECS=\"").append(transcode.getActualCodecs()).append("\"");
            }
            if (StringUtils.isNotBlank(transcode.getActualFrameRate())) {
                stringBuilder.append(",FRAME-RATE=").append(transcode.getActualFrameRate());
            }
            if (StringUtils.isNotBlank(transcode.getActualDynamicRange())
                    && !"SDR".equals(transcode.getActualDynamicRange())) {
                stringBuilder.append(",VIDEO-RANGE=").append(
                        "HLG".equals(transcode.getActualDynamicRange()) ? "HLG" : "PQ");
            }
            stringBuilder.append("\n")
                    .append(m3u8Url)
                    .append("\n");
        }
        return stringBuilder.toString();
    }
}
