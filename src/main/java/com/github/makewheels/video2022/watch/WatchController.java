package com.github.makewheels.video2022.watch;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@Controller
public class WatchController {
    @RequestMapping("/watch")
    public String watch(HttpServletRequest request) {
        return "watch-hls";
    }
}
