package com.github.makewheels.video2022;

import com.github.makewheels.video2022.transcode.TranscodeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class Video2022ApplicationTests {
    @Resource
    private TranscodeService transcodeService;

    @Test
    void contextLoads() {
        transcodeService.onVideoReady("624be4bd8bd8926b18d541ed");
    }

}
