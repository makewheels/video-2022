package com.github.makewheels.video2022.springboot.health;

import cn.hutool.core.date.DateUtil;
import com.github.makewheels.video2022.system.context.RequestUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;

@RestController
@Slf4j
public class HealthCheckController {
    @GetMapping("healthCheck")
    public String healthCheck() {
        HttpServletRequest request = RequestUtil.getRequest();
        log.info("healthCheck---" + DateUtil.formatDateTime(new Date()) + "-" + request.getRequestURL());
        return "ok " + System.currentTimeMillis();
    }
}
