package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.user.session;

import cn.hutool.json.JSONObject;
import com.github.makewheels.video2022.system.response.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController()
@RequestMapping("session")
public class SessionController {
    @Resource
    private SessionService sessionService;

    @GetMapping("requestSessionId")
    public Result<JSONObject> requestSessionId() {
        return sessionService.requestSessionId();
    }
}
