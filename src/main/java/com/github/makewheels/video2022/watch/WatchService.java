package com.github.makewheels.video2022.watch;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.etc.ip.IpService;
import com.github.makewheels.video2022.etc.response.Result;
import com.github.makewheels.video2022.utils.DingUtil;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.Video;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;

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

        Video video = mongoTemplate.findById(videoId, Video.class);

        //推送钉钉
        String province = ipResult.getString("province");
        String city = ipResult.getString("city");
        String district = ipResult.getString("district");
        log.info("观看记录：videoId = {}, title = {}, {} {} {} {}",
                videoId, video.getTitle(), ip, province, city, district);

        String markdownText =
                "# video: " + video.getTitle() + "\n\n" +
                        "# viewCount: " + video.getWatchCount() + "\n\n" +
                        "# ip: " + ip + "\n\n" +
                        "# ipInfo: " + province + " " + city + " " + district + "\n\n" +
                        "# User-Agent: " + userAgent;
        DingUtil.sendMarkdown("观看记录", markdownText);

        return Result.ok();
    }
}
