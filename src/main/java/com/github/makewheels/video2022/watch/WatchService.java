package com.github.makewheels.video2022.watch;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.etc.ip.IpService;
import com.github.makewheels.video2022.etc.response.Result;
import com.github.makewheels.video2022.file.File;
import com.github.makewheels.video2022.file.FileRepository;
import com.github.makewheels.video2022.transcode.Transcode;
import com.github.makewheels.video2022.transcode.TranscodeRepository;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.utils.DingUtil;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.Video;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
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
     * 根据转码对象获取m3u8内容，返回String
     */
    public String getM3u8Content(
            User user, String videoId, String clientId, String sessionId,
            String transcodeId, String resolution) {

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
}
