package com.github.makewheels;

import cn.hutool.core.date.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

@RestController
@Slf4j
public class HealthCheckController {
    @GetMapping("healthCheck")
    public String healthCheck() {
        log.info("healthCheck-" + DateUtil.formatDateTime(new Date()));
        return "ok " + System.currentTimeMillis();
    }
}
