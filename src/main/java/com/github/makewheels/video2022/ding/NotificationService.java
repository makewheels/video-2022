package com.github.makewheels.video2022.ding;

import com.alibaba.fastjson.JSONObject;
import com.dingtalk.api.response.OapiRobotSendResponse;
import com.github.makewheels.video2022.cover.CoverService;
import com.github.makewheels.video2022.etc.context.RequestUtil;
import com.github.makewheels.video2022.etc.exception.ExceptionLog;
import com.github.makewheels.video2022.video.bean.entity.Video;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 通知服务
 */
@Service
public class NotificationService {
    @Value("${external-base-url}")
    private String externalBaseUrl;

    @Resource
    private DingService dingService;
    @Resource
    private CoverService coverService;

    /**
     * 发送视频就绪消息
     */
    public OapiRobotSendResponse sendVideoReadyMessage(Video video) {
        String videoTitle = video.getTitle();
        String coverUrl = coverService.getSignedCoverUrl(video.getCoverId());
        String messageTitle = "视频就绪: " + videoTitle;
        String markdownText = "视频就绪"
                + "\n\n" + video.getId()
                + "\n\n" + videoTitle
                + "\n\n![" + videoTitle + "](" + coverUrl + ")";
        return dingService.sendMarkdown(RobotType.WATCH_LOG, messageTitle, markdownText);
    }

    /**
     * 发送观看记录消息
     */
    public OapiRobotSendResponse sendWatchLogMessage(Video video, JSONObject ipInfo) {
        String videoTitle = video.getTitle();
        String coverUrl = coverService.getSignedCoverUrl(video.getCoverId());

        String messageTitle = "观看记录: " + videoTitle;
        String markdownText = "# video: " + videoTitle + "\n\n" +
                "# viewCount: " + video.getWatchCount() + "\n\n" +
                "# ip: " + ipInfo.getString("ip") + "\n\n" +
                "# ipInfo: " + ipInfo.getString("province") + " "
                + ipInfo.getString("city") + " "
                + ipInfo.getString("district") + "\n\n"
                + "# User-Agent:\n\n" + RequestUtil.getUserAgent()
                + "\n\n![" + videoTitle + "](" + coverUrl + ")";
        return dingService.sendMarkdown(RobotType.WATCH_LOG, messageTitle, markdownText);
    }

    /**
     * 发送异常消息
     */
    public void sendExceptionMessage(Exception e, ExceptionLog exceptionLog) {
        String messageTitle = "异常信息";

        String clickUrl = externalBaseUrl + "/exceptionLog/getById?exceptionLogId=" + exceptionLog.getId();

        String exceptionStackTrace = StringUtils.substring(
                exceptionLog.getExceptionStackTrace(), 0, 500);

        String markdownClickUrl = "[点击查看异常 " + exceptionLog.getId() + "](" + clickUrl + ")";

        String markdownText = "message: " + e.getMessage()
                + "\n\n" + markdownClickUrl
                + "\n\n```" + exceptionStackTrace + "```"
                + "\n\n" + markdownClickUrl;

        dingService.sendMarkdown(RobotType.EXCEPTION, messageTitle, markdownText);
    }

}