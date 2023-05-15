package com.github.makewheels.video2022.ding;

import com.alibaba.fastjson.JSONObject;
import com.dingtalk.api.response.OapiRobotSendResponse;
import com.github.makewheels.video2022.etc.context.RequestUtil;
import com.github.makewheels.video2022.etc.exception.ExceptionLog;
import com.github.makewheels.video2022.video.bean.entity.Video;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 通知服务
 */
@Service
public class NotificationService {
    @Resource
    private DingService dingService;
    @Value("${external-base-url}")
    private String externalBaseUrl;


    /**
     * 发送视频就绪消息
     */
    public OapiRobotSendResponse sendVideoReadyMessage(Video video) {
        String videoTitle = video.getTitle();
        String messageTitle = "视频就绪: " + videoTitle;
        String markdownText = "视频就绪\n\n" + video.getId() + "\n\n" + videoTitle;
        return dingService.sendMarkdown(RobotType.WATCH_LOG, messageTitle, markdownText);
    }

    /**
     * 发送观看记录消息
     */
    public OapiRobotSendResponse sendWatchLogMessage(Video video, JSONObject ipInfo) {
        String videoTitle = video.getTitle();
        String messageTitle = "观看记录: " + videoTitle;
        String markdownText = "# video: " + videoTitle + "\n\n" +
                "# viewCount: " + video.getWatchCount() + "\n\n" +
                "# ip: " + ipInfo.getString("ip") + "\n\n" +
                "# ipInfo: " + ipInfo.getString("province") + " "
                + ipInfo.getString("city") + " "
                + ipInfo.getString("district") + "\n\n\n\n"
                + "# User-Agent: " + RequestUtil.getUserAgent();
        return dingService.sendMarkdown(RobotType.WATCH_LOG, messageTitle, markdownText);
    }

    /**
     * 发送异常消息
     */
    public void sendExceptionMessage(Exception e, ExceptionLog exceptionLog) {
        String messageTitle = e.getMessage();
        String clickUrl = externalBaseUrl + "/exception/getById?" + exceptionLog.getId();
        String markdownText = e.getMessage() + "\n\n```" + exceptionLog.getExceptionStackTrace() + "```"
                + "\n\n[点击查看" + exceptionLog.getId() + "](" + clickUrl + ")";
        dingService.sendMarkdown(RobotType.EXCEPTION, messageTitle, markdownText);
    }

}
