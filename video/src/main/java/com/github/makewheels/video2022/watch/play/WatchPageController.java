package com.github.makewheels.video2022.watch.play;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class WatchPageController {
    @RequestMapping("/watch")
    public String watch() {
        return "watch";
    }

    @RequestMapping("/w")
    public String w() {
        return "watch";
    }

}
