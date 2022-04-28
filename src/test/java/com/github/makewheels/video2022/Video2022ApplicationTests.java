package com.github.makewheels.video2022;

import com.github.makewheels.video2022.transcode.TranscodeCallbackService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class Video2022ApplicationTests {
    @Resource
    private TranscodeCallbackService transcodeCallbackService;

    @Test
    void contextLoads() {
    }

}
