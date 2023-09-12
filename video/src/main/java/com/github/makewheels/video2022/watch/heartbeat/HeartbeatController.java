package com.github.makewheels.video2022.watch.heartbeat;

import com.github.makewheels.video2022.etc.system.response.Result;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("heartbeat")
public class HeartbeatController {
    @Resource
    private HeartbeatService heartbeatService;

    @RequestMapping("add")
    public Result<Void> add(@RequestBody Heartbeat heartbeat) {
        return heartbeatService.add(heartbeat);
    }
}
