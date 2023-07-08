package com.github.makewheels;

import cn.hutool.core.date.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

@RestController
@Slf4j
public class HealthCheckController {
    @GetMapping("2016-08-15/proxy/video-2022-hongkong.LATEST/video-2022-youtube/healthCheck")
    public String healthCheck1() {
        log.info("healthCheck1-" + DateUtil.formatDateTime(new Date()));
        return "ok " + System.currentTimeMillis();
    }

    @GetMapping("healthCheck")
    public String healthCheck2() {
        log.info("healthCheck2-" + DateUtil.formatDateTime(new Date()));
        return "ok " + System.currentTimeMillis();
    }
}
