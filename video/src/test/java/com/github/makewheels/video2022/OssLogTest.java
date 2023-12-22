package com.github.makewheels.video2022;

import com.github.makewheels.video2022.oss.osslog.OssLogService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import javax.annotation.Resource;

@SpringBootTest(classes = VideoApplication.class)
public class OssLogTest {
    @Resource
    private OssLogService ossLogService;

    /**
     * 生成快照
     */
    @Test
    public void generateLog() {
        LocalDate date = LocalDate.of(2023, 10, 4);
        ossLogService.generateOssAccessLog(LocalDate.now().plusDays(-2));
    }
}
