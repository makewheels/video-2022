package com.github.makewheels.video2022;

import com.github.makewheels.video2022.transcode.factory.TranscodeFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
public class TestTranscodeFactory {
    @Resource
    private TranscodeFactory transcodeFactory;

    @Test
    public void test() {
        transcodeFactory.getTranscodeService("af");
    }
}
