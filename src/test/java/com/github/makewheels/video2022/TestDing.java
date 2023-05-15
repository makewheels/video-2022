package com.github.makewheels.video2022;

import com.github.makewheels.video2022.ding.DingService;
import com.github.makewheels.video2022.ding.RobotType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.UUID;

@SpringBootTest
public class TestDing {
    @Resource
    private DingService dingService;

    @Test
    public void test() {
        String text = "测试消息-test-message-" + UUID.randomUUID();
        dingService.sendMarkdown(RobotType.WATCH_LOG, "test-title", text);
    }
}
