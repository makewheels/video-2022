package com.github.makewheels.video2022.watch;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class WatchPageController {
    @RequestMapping("/watch")
    public String watch() {
        return "watch";
    }
}
