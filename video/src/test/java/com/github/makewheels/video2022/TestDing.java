package com.github.makewheels.video2022;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.etc.ding.DingService;
import com.github.makewheels.video2022.etc.ding.NotificationService;
import com.github.makewheels.video2022.etc.ding.RobotType;
import com.github.makewheels.video2022.etc.system.context.RequestUtil;
import com.github.makewheels.video2022.utils.IpService;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.entity.Video;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.UUID;

@SpringBootTest
public class TestDing {
    @Resource
    private DingService dingService;
    @Resource
    private NotificationService notificationService;
    @Resource
    private VideoRepository videoRepository;
    @Resource
    private IpService ipService;

    /**
     * 普通测试钉钉
     */
    @Test
    public void testDing() {
        String text = "测试消息-test-message-" + UUID.randomUUID();
        dingService.sendMarkdown(RobotType.WATCH_LOG, "test-title", text);
    }

    /**
     * 发送视频就绪消息
     */
    @Test
    public void testSendVideoReadyMessage() {
        Video video = videoRepository.getById("646401931286d141d607bb22");
        notificationService.sendVideoReadyMessage(video);
    }

    /**
     * 发送观看记录消息
     */
    @Test
    public void testSendWatchLogMessage() {
        String ip = RequestUtil.getIp();
        JSONObject ipResult = ipService.getIpWithRedis(ip);
        String province = ipResult.getString("province");
        String city = ipResult.getString("city");
        String district = ipResult.getString("district");

        Video video = videoRepository.getById("646401931286d141d607bb22");
        notificationService.sendWatchLogMessage(video, ipResult);
    }
}
