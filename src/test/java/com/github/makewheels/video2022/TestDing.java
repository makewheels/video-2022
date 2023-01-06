package com.github.makewheels.video2022;

import com.github.makewheels.video2022.utils.DingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.UUID;

@SpringBootTest
public class TestDing {
    @Resource
    private DingService dingService;

    @Test
    public void testDing() {
        String text = "测试消息-test-message-" + UUID.randomUUID();
        dingService.sendMarkdown("test-title", text);
    }
}
