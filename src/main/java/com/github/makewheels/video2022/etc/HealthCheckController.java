package com.github.makewheels.video2022.etc;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthCheckController {
    @GetMapping("healthCheck")
    public String healthCheck() {
        return "ok " + System.currentTimeMillis();
    }
}
