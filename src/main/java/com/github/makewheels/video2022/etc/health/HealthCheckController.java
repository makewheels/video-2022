package com.github.makewheels.video2022.etc.health;

import cn.hutool.core.date.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;

@RestController
@Slf4j
public class HealthCheckController {
    @GetMapping("healthCheck")
    public String healthCheck(HttpServletRequest request) {
        log.info("healthCheck-" + DateUtil.formatDateTime(new Date()) + "-" + request.getRequestURL());
        return "ok " + System.currentTimeMillis();
    }
}
