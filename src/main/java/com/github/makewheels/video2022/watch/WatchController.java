package com.github.makewheels.video2022.watch;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;

@Controller
public class WatchController {
    @RequestMapping("/watch")
    public String watch(HttpServletRequest request) {
        return "watch-hls";
    }
}
