package com.github.makewheels.video2022.oss.osslog;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDate;

/**
 * 保存阿里云OSS访问日志
 */
@Component
@Slf4j
public class GenerateOssAccessLogTask {
    @Resource
    private OssLogService ossLogService;

    /**
     * 每天零点，获取两天前的访问日志
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void generateOssAccessLog() {
        ossLogService.generateOssAccessLog(LocalDate.now().plusDays(-2));
    }
}
