package com.github.makewheels.video2022.watch;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ShortIdController {
    @Value("${internal-base-url}")
    private String baseUrl;

//    @GetMapping("/{shortId}")
    public String toWatchPage(@PathVariable("shortId") String shortId) {
        return "redirect:" + baseUrl + "/watch?v=" + shortId;
    }
}
