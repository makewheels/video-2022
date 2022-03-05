package com.github.makewheels.video2022;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.usermicroservice2022.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("video")
@Slf4j
public class VideoController {
    @Resource
    private UserServiceClient userServiceClient;

    @GetMapping("create")
    public String create(HttpServletRequest request) {
        String token = request.getHeader("token");
        User user = userServiceClient.getUserByToken(token);
        log.info(JSON.toJSONString(user));
        return System.currentTimeMillis() + "";
    }
}
