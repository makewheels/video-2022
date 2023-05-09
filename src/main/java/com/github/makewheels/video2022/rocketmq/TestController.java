package com.github.makewheels.video2022.rocketmq;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
public class TestController {
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @GetMapping("/sendmsg")
    public String sendMessage() {
        rocketMQTemplate.convertAndSend("BenchmarkTest", "Hello, RocketMQ!");
        return "Message sent";
    }
}