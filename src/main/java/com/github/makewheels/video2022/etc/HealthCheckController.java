package com.github.makewheels.video2022.etc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@Slf4j
public class HealthCheckController {
    @GetMapping("healthCheck")
    public String healthCheck(HttpServletRequest request) {
        log.info("healthCheck " + request.getRequestURL());
        return request.getRequestURL() + " " + System.currentTimeMillis();
    }
}